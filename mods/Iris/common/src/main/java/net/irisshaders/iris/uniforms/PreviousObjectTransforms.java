// AGENT-DONE(codex): fsr2-transform-cache
package net.irisshaders.iris.uniforms;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * TODO(FSR2): per-object previous-frame transform cache, feeding DNCity's real per-object
 * motion-vector requirement (see AGENTS.md's "Architecture: FSR2 temporal upscaling").
 * <p>
 * Design (not yet implemented -- this is a signature-only scaffold):
 * <ul>
 *   <li>Identity-keyed (not equals/hashCode-keyed -- two entities can be "equal" transiently
 *       during a tick in ways that would corrupt a regular HashMap) caches of the last frame's
 *       fully-interpolated model matrix, one for {@link Entity} and one for {@link BlockEntity}.
 *   <li>{@link #getPrevious(Entity)} / {@link #getPrevious(BlockEntity)} return the cached matrix
 *       if present, or {@code null} if this object has never been seen -- callers (the
 *       entity/block-entity render-dispatcher mixins, see MixinEntityRenderDispatcher's own TODO)
 *       must treat {@code null} as "previous == current", i.e. zero velocity, which is the
 *       correct FSR2 convention for a newly-disoccluded object.
 *   <li>{@link #update(Entity, Matrix4fc)} / {@link #update(BlockEntity, Matrix4fc)} overwrite the
 *       cache entry with this frame's transform, to be read back as "previous" next frame. Must
 *       be called once per rendered object per frame, after this frame's lookup.
 *   <li>{@link #onFrameEnd()} is where stale entries (objects no longer rendered -- e.g. an
 *       entity that left render distance or despawned) should be pruned, to avoid an unbounded
 *       memory leak across a long play session. Exact pruning strategy (age-based? explicit
 *       "seen this frame" marking + sweep?) is not decided yet.
 *   <li>The "hand" (first-person view-model rendering, see HandRenderer.java) is NOT a real
 *       {@link Entity} -- it needs its own sentinel-keyed slot, not modeled here yet; see the
 *       "Hand" callout in AGENTS.md's FSR2 section.
 * </ul>
 * Thread-safety: assumed render-thread-only, same as the rest of Iris's per-frame uniform state
 * (e.g. {@link CapturedRenderingState}) -- not verified/enforced here.
 */
public final class PreviousObjectTransforms {
	public static final PreviousObjectTransforms INSTANCE = new PreviousObjectTransforms();

	private final Map<Entity, Matrix4f> previousEntityTransforms = new IdentityHashMap<>();
	private final Map<BlockEntity, Matrix4f> previousBlockEntityTransforms = new IdentityHashMap<>();
	private final Set<Entity> renderedEntities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<BlockEntity> renderedBlockEntities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

	private PreviousObjectTransforms() {
	}

	/**
	 * @return last frame's fully-interpolated model matrix for {@code entity}, or {@code null} if
	 * this entity has no cached previous transform yet (treat as zero velocity).
	 */
	public Matrix4fc getPrevious(Entity entity) {
		Matrix4f transform = previousEntityTransforms.get(entity);
		return transform == null ? null : new Matrix4f(transform);
	}

	/**
	 * @return last frame's fully-interpolated model matrix for {@code blockEntity}, or
	 * {@code null} if never seen before (treat as zero velocity).
	 */
	public Matrix4fc getPrevious(BlockEntity blockEntity) {
		Matrix4f transform = previousBlockEntityTransforms.get(blockEntity);
		return transform == null ? null : new Matrix4f(transform);
	}

	/**
	 * Records {@code currentTransform} as the transform to serve as "previous" on the next frame's
	 * {@link #getPrevious(Entity)} call for this same entity.
	 */
	public void update(Entity entity, Matrix4fc currentTransform) {
		previousEntityTransforms.put(entity, new Matrix4f(currentTransform));
		renderedEntities.add(entity);
	}

	/**
	 * Records {@code currentTransform} as the transform to serve as "previous" on the next frame's
	 * {@link #getPrevious(BlockEntity)} call for this same block entity.
	 */
	public void update(BlockEntity blockEntity, Matrix4fc currentTransform) {
		previousBlockEntityTransforms.put(blockEntity, new Matrix4f(currentTransform));
		renderedBlockEntities.add(blockEntity);
	}

	/** Removes transforms for objects that were not rendered during the completed frame. */
	public void onFrameEnd() {
		previousEntityTransforms.keySet().retainAll(renderedEntities);
		previousBlockEntityTransforms.keySet().retainAll(renderedBlockEntities);
		renderedEntities.clear();
		renderedBlockEntities.clear();
	}
}
