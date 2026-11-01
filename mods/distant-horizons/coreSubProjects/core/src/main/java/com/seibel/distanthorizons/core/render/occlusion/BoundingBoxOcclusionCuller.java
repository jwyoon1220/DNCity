/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.render.occlusion;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL43;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNCity: GPU hardware occlusion culling for {@link com.seibel.distanthorizons.core.render.RenderBufferHandler},
 * on top of its existing CPU-only frustum culling. One instance is owned by one
 * {@link com.seibel.distanthorizons.core.render.RenderBufferHandler} (so cached visibility never leaks
 * across levels/dimensions); the GL mesh/shader used to actually draw a query box are lazily created once
 * and shared (stateless, safe to share across instances on the same render thread).
 *
 * <h2>How this differs from a "real" temporal occlusion culler</h2>
 * A textbook GPU-driven occlusion culler issues a section's bounding-box query <i>after</i> that frame's
 * own opaque geometry pass (so occlusion by <i>other, nearer LOD sections drawn this same frame</i> is
 * caught too), then consumes the result on a later frame. This class instead issues + (opportunistically)
 * consumes queries entirely from within {@code buildRenderList}, which runs <i>before</i> this frame's own
 * LOD opaque pass -- so a query here only ever tests against whatever was already in the depth buffer at
 * that point (vanilla/Sodium terrain, entities -- DH's own render hook fires after vanilla terrain
 * rendering, see {@code RenderUtil}'s near-clip-plane/overdraw-prevention logic, which relies on the same
 * ordering). That's a real limitation -- a far LOD section hidden behind a *closer LOD section* (not
 * behind vanilla terrain) won't be culled by this -- but it's a deliberate trade: it keeps every new GL
 * call self-contained inside this one method (no interleaving with {@code LodRenderer}'s draw passes, no
 * new interaction with Iris's shadow pass to reason about), which matters a lot for a feature that
 * couldn't be visually verified before shipping -- see {@code Config...Culling.enableOcclusionCulling}'s
 * own comment. It's still a real, useful GPU-driven win: LOD terrain behind actual mountains/hills within
 * vanilla render distance gets skipped instead of submitted to the GPU every frame.
 *
 * <p>Each section's query uses its <b>full column height</b> (world min/max Y) as a conservative bounding
 * box, since accurate per-section terrain height isn't tracked on the CPU side (it's baked directly into
 * each section's vertex buffer -- see {@code LodBufferContainer}). This means it under-culls rather than
 * over-culls: it can never wrongly hide something that's actually visible, it just won't catch every case
 * a height-accurate box would.
 *
 * <p>Not thread-safe -- like the rest of {@code RenderBufferHandler}, only ever used from the render
 * thread.
 */
public class BoundingBoxOcclusionCuller implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	/** After this many frames of not being tested (section no longer in the quad tree / never
	 * revisited), a cache entry's native query object is freed so this doesn't grow unbounded. */
	private static final long STALE_FRAME_THRESHOLD = 300; // ~5 seconds at 60 FPS
	/** Bounds the per-call cost of the stale-entry sweep so it can run every frame unconditionally. */
	private static final int MAX_EVICTIONS_PER_SWEEP = 64;

	private static boolean meshInitialized = false;
	private static int vao = -1;
	private static int vbo = -1;
	private static int ebo = -1;
	private static ShaderProgram boxShader = null;
	private static int uniformMvpLocation = -1;


	private static class QueryEntry
	{
		final int queryId;
		boolean pending = false;
		/** Defaults to visible: a section that hasn't been tested yet should never be hidden. */
		boolean lastVisible = true;
		long lastSeenFrame;

		QueryEntry(int queryId, long frame) { this.queryId = queryId; this.lastSeenFrame = frame; }
	}

	private final Map<Long, QueryEntry> queryByPos = new ConcurrentHashMap<>();
	private long frameCounter = 0;



	//==========================//
	// per-frame cache upkeep   //
	//==========================//

	/** Call once per frame, before any {@link #testAndQueue} calls -- consumes results from queries
	 * issued on a previous frame and evicts long-untouched cache entries. */
	public void beginFrame()
	{
		this.frameCounter++;

		int evictions = 0;
		Iterator<Map.Entry<Long, QueryEntry>> it = this.queryByPos.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Long, QueryEntry> mapEntry = it.next();
			QueryEntry entry = mapEntry.getValue();

			if (entry.pending)
			{
				try
				{
					int available = GL43.glGetQueryObjecti(entry.queryId, GL43.GL_QUERY_RESULT_AVAILABLE);
					if (available != 0)
					{
						int samplesPassed = GL43.glGetQueryObjecti(entry.queryId, GL43.GL_QUERY_RESULT);
						entry.lastVisible = (samplesPassed != 0);
						entry.pending = false;
					}
					// if not yet available, leave pending and keep last known visibility this frame
				}
				catch (Exception e)
				{
					// don't let a GL hiccup permanently hide terrain
					entry.lastVisible = true;
					entry.pending = false;
					LOGGER.error("Unexpected issue reading occlusion query result, error: [" + e.getMessage() + "].", e);
				}
			}

			if (evictions < MAX_EVICTIONS_PER_SWEEP && (this.frameCounter - entry.lastSeenFrame) > STALE_FRAME_THRESHOLD)
			{
				GL43.glDeleteQueries(entry.queryId);
				it.remove();
				evictions++;
			}
		}
	}

	/**
	 * Returns whether [sectionPos]'s bounding box was visible as of its last resolved query (defaults
	 * to {@code true} -- never visible-tested yet), and queues a fresh query for it (skipped if one is
	 * already in flight) so future frames can revisit the answer.
	 */
	public boolean testAndQueue(long sectionPos, float minX, float minY, float minZ, float sizeX, float sizeY, float sizeZ, Matrix4fc worldViewProjection)
	{
		initMeshAndShaderIfNeeded();

		QueryEntry entry = this.queryByPos.get(sectionPos);
		if (entry == null)
		{
			entry = new QueryEntry(GL43.glGenQueries(), this.frameCounter);
			this.queryByPos.put(sectionPos, entry);
		}
		else
		{
			entry.lastSeenFrame = this.frameCounter;
		}

		if (!entry.pending)
		{
			try
			{
				Matrix4f boxMvp = new Matrix4f(worldViewProjection).translate(minX, minY, minZ).scale(sizeX, sizeY, sizeZ);

				GL43.glDepthMask(false);
				GL43.glColorMask(false, false, false, false);

				boxShader.bind();
				boxShader.setUniform(uniformMvpLocation, new Mat4f(boxMvp));
				GL43.glBindVertexArray(vao);

				GL43.glBeginQuery(GL43.GL_ANY_SAMPLES_PASSED_CONSERVATIVE, entry.queryId);
				GL43.glDrawElements(GL43.GL_TRIANGLES, 36, GL43.GL_UNSIGNED_SHORT, 0);
				GL43.glEndQuery(GL43.GL_ANY_SAMPLES_PASSED_CONSERVATIVE);

				GL43.glBindVertexArray(0);
				boxShader.unbind();
				GL43.glColorMask(true, true, true, true);
				GL43.glDepthMask(true);

				entry.pending = true;
			}
			catch (Exception e)
			{
				// never let a GL issue here block real rendering -- treat as visible and move on
				LOGGER.error("Unexpected issue issuing occlusion query, error: [" + e.getMessage() + "].", e);
				return true;
			}
		}

		return entry.lastVisible;
	}



	//==============//
	// GL resources //
	//==============//

	/** Unit cube in [0,1]^3, 8 corners, 12 triangles (36 indices) -- transformed per box via the MVP
	 * uniform (see {@link #testAndQueue}), so this mesh is created once and reused for every section. */
	private static void initMeshAndShaderIfNeeded()
	{
		if (meshInitialized)
		{
			return;
		}
		meshInitialized = true;

		float[] positions = {
				0, 0, 0,  1, 0, 0,  1, 1, 0,  0, 1, 0, // -Z face corners
				0, 0, 1,  1, 0, 1,  1, 1, 1,  0, 1, 1, // +Z face corners
		};
		short[] indices = {
				0, 1, 2,  0, 2, 3, // -Z
				5, 4, 7,  5, 7, 6, // +Z
				4, 0, 3,  4, 3, 7, // -X
				1, 5, 6,  1, 6, 2, // +X
				3, 2, 6,  3, 6, 7, // +Y (top)
				4, 5, 1,  4, 1, 0, // -Y (bottom)
		};

		vao = GL43.glGenVertexArrays();
		GL43.glBindVertexArray(vao);

		vbo = GL43.glGenBuffers();
		GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER, vbo);
		GL43.glBufferData(GL43.GL_ARRAY_BUFFER, positions, GL43.GL_STATIC_DRAW);
		GL43.glVertexAttribPointer(0, 3, GL43.GL_FLOAT, false, 0, 0);
		GL43.glEnableVertexAttribArray(0);

		ebo = GL43.glGenBuffers();
		GL43.glBindBuffer(GL43.GL_ELEMENT_ARRAY_BUFFER, ebo);
		GL43.glBufferData(GL43.GL_ELEMENT_ARRAY_BUFFER, indices, GL43.GL_STATIC_DRAW);

		GL43.glBindVertexArray(0);

		// no fragment output is ever read (color writes are disabled while this is used, see
		// testAndQueue) -- the fragment shader just needs to exist for the program to link.
		String vertSrc = ""
				+ "#version 150 core\n"
				+ "in vec3 aPos;\n"
				+ "uniform mat4 uMvp;\n"
				+ "void main() { gl_Position = uMvp * vec4(aPos, 1.0); }\n";
		String fragSrc = ""
				+ "#version 150 core\n"
				+ "out vec4 fragColor;\n"
				+ "void main() { fragColor = vec4(0.0); }\n";

		boxShader = new ShaderProgram(() -> vertSrc, () -> fragSrc, "fragColor", new String[]{ "aPos" });
		uniformMvpLocation = boxShader.getUniformLocation("uMvp");
	}



	//=========//
	// cleanup //
	//=========//

	/** Releases this instance's outstanding native query objects -- does not touch the shared mesh/shader
	 * (those are process-lifetime, matching how other DH renderer singletons like SSAORenderer/
	 * FogRenderer/DhFadeRenderer are never explicitly freed either). */
	@Override
	public void close()
	{
		for (QueryEntry entry : this.queryByPos.values())
		{
			GL43.glDeleteQueries(entry.queryId);
		}
		this.queryByPos.clear();
	}
}
