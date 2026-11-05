// AGENT-DONE(claude): dagor-lod-scaling
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
package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.pos.DhSectionPos;

import java.util.concurrent.ConcurrentHashMap;

/**
 * DNCity: the distance {@link LodQuadTree} picks a section's detail level from, made to behave like War Thunder's
 * (Dagor Engine's) LOD distances instead of DH's plain horizontal distance. Two rules, both read in Dagor's public
 * source (GaijinEntertainment/DagorEngine):
 *
 * <ul>
 *     <li><b>Height counts.</b> Dagor's terrain LOD ({@code prog/gameLibs/heightmap/heightmapMetricsCulling.cpp},
 *     {@code recursive_split}) measures a patch from the camera in 3D, against the patch's own height bounds. DH only
 *     uses the horizontal distance, so the terrain right under an aircraft at 1,500 m is loaded at full detail. Here
 *     the vertical gap between the camera and the highest terrain DH has meshed in that region is added
 *     (Pythagoras). Only a camera <em>above</em> the terrain gains anything, so nothing on the ground changes.</li>
 *     <li><b>Zoom counts.</b> Dagor's {@code WorldRenderer::updateLodsScaling} ({@code prog/daNetGame/render/world/
 *     worldRenderer.cpp}) multiplies LOD distances by the shooter camera's zoom multiplier and never lets it go below
 *     the default. Here the zoom is {@code tan(defaultFov/2) / tan(fov/2)}, the factor the projected size of
 *     anything grows by, rounded down to a power of two and capped ({@link #MAX_ZOOM_MUL}).</li>
 * </ul>
 *
 * <p>DESIGN values (no Dagor number was copied): the power-of-two steps, the cap and the zoom-out delay
 * ({@link #ZOOM_OUT_DELAY_NANOS}) exist so a scope flicking in and out does not reload the whole LOD tree each time.
 *
 * <p>Threads: the zoom and camera height are written on the render thread and read by the quad tree's own update
 * thread (volatile); the height cache is written where buffers finish uploading (render thread).
 */
public final class LodDetailScaling
{
	/** Highest zoom multiplier applied (a 4x scope or a spyglass both end up here). */
	public static final double MAX_ZOOM_MUL = 32.0;
	/** Highest multiplier of the loaded/drawn LOD radius (the tree is built this much wider). */
	public static final double MAX_RANGE_MUL = 8.0;
	/** A lower zoom multiplier only takes over after it has been seen continuously for this long. */
	public static final long ZOOM_OUT_DELAY_NANOS = 2_000_000_000L;

	/** Terrain heights are kept per region of this section detail level (2^9 = 512 blocks wide). */
	private static final byte REGION_DETAIL_LEVEL = 9;
	/** The cache is dropped (and refills from uploads) if it ever grows past this. */
	private static final int MAX_REGIONS = 16_384;

	private static volatile double zoomMul = 1.0;
	private static volatile double cameraY = Double.NaN;
	private static double pendingLowerZoom = Double.NaN;
	private static long pendingLowerSinceNanos = 0;

	private static final ConcurrentHashMap<Long, Integer> REGION_MAX_Y = new ConcurrentHashMap<>();

	private LodDetailScaling() { }



	//=================//
	// render thread   //
	//=================//

	/**
	 * Call once per frame from the render thread.
	 *
	 * @param fovDegrees current vertical FOV including zoom (scope, spyglass, sprint)
	 * @param defaultFovDegrees the player's FOV setting
	 */
	public static void updateCamera(double cameraWorldY, double fovDegrees, double defaultFovDegrees, boolean zoomEnabled)
	{
		cameraY = cameraWorldY;

		double target = 1.0;
		if (zoomEnabled && fovDegrees > 0.0 && defaultFovDegrees > 0.0 && fovDegrees < defaultFovDegrees)
		{
			double raw = Math.tan(Math.toRadians(defaultFovDegrees) * 0.5) / Math.tan(Math.toRadians(fovDegrees) * 0.5);
			// nearest power of two (in log space), so a 24x scope gets 32x rather than 16x
			target = Math.min(MAX_ZOOM_MUL, Math.max(1.0, Math.pow(2.0, Math.round(Math.log(raw) / Math.log(2.0)))));
		}

		double current = zoomMul;
		if (target >= current)
		{
			// zooming in (or staying): immediately, like Dagor
			zoomMul = target;
			pendingLowerZoom = Double.NaN;
			return;
		}

		long now = System.nanoTime();
		if (Double.isNaN(pendingLowerZoom) || pendingLowerZoom != target)
		{
			pendingLowerZoom = target;
			pendingLowerSinceNanos = now;
		}
		else if (now - pendingLowerSinceNanos >= ZOOM_OUT_DELAY_NANOS)
		{
			zoomMul = target;
			pendingLowerZoom = Double.NaN;
		}
	}

	/** Records the highest meshed terrain of a section that just finished uploading. */
	public static void recordTerrainTop(long sectionPos, int maxBlockY)
	{
		if (REGION_MAX_Y.size() > MAX_REGIONS)
		{
			REGION_MAX_Y.clear();
		}

		if (DhSectionPos.getDetailLevel(sectionPos) >= REGION_DETAIL_LEVEL)
		{
			return; // its top says nothing about which region it is in
		}
		
		REGION_MAX_Y.merge(regionKey(sectionPos), maxBlockY, Math::max);
	}

	/** Level change: heights of another world mean nothing. */
	public static void clearTerrainHeights() { REGION_MAX_Y.clear(); }



	//==============//
	// any thread   //
	//==============//

	public static double getZoomMul() { return zoomMul; }

	/** How much wider than the configured radius the LOD tree is built (1 when the feature is off). */
	public static double rangeCapMul()
	{
		return com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.zoomExtendsLodRange.get() ? MAX_RANGE_MUL : 1.0;
	}

	/** Current multiplier of the loaded/drawn LOD radius: the scope zoom, 1 when the feature is off. */
	public static double rangeMul()
	{
		return com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.zoomExtendsLodRange.get() ? Math.min(zoomMul, MAX_RANGE_MUL) : 1.0;
	}

	/**
	 * The distance to pick {@code sectionPos}'s detail level from.
	 *
	 * @param horizontalDistance DH's own distance from the player to the section's centre (blocks)
	 * @param useHeight whether the vertical gap above the region's terrain counts
	 */
	public static double effectiveDistance(double horizontalDistance, long sectionPos, boolean useHeight)
	{
		double distance = horizontalDistance;

		double camY = cameraY;
		// a section wider than a region could hold higher terrain in a region the cache does not look at: no height
		// for those (they are far and coarse anyway; their children get it once the tree gets down to them)
		if (useHeight && !Double.isNaN(camY) && DhSectionPos.getDetailLevel(sectionPos) < REGION_DETAIL_LEVEL)
		{
			Integer top = REGION_MAX_Y.get(regionKey(sectionPos));
			if (top != null)
			{
				double gap = camY - top;
				if (gap > 0.0)
				{
					distance = Math.sqrt(distance * distance + gap * gap);
				}
			}
		}

		return distance / zoomMul;
	}

	/** The region a section lies in; only called for sections smaller than a region. */
	private static long regionKey(long sectionPos)
	{
		return DhSectionPos.convertToDetailLevel(sectionPos, REGION_DETAIL_LEVEL);
	}

}
