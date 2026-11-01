package net.irisshaders.iris.uniforms;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

public class CapturedRenderingState {
	public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

	private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

	private Matrix4fc gbufferModelView;
	private Matrix4fc gbufferProjection;
	private Vector3d fogColor;
	private float fogDensity;
	private float darknessLightFactor;
	private float tickDelta;
	private float realTickDelta;
	private int currentRenderedBlockEntity;

	private int currentRenderedEntity = -1;
	private int currentRenderedItem = -1;

	private float currentAlphaTest;
	private float cloudTime;

	// TODO(FSR2): plumbing for AGENTS.md's "Architecture: FSR2 temporal upscaling". These are
	// plain data holders (safe to implement fully, unlike the throwing TODO stubs elsewhere in
	// this scaffold) but nothing sets or reads them yet -- setCurrentEntityPreviousModelMatrix
	// needs to be called from MixinEntityRenderDispatcher's per-draw hook (see its own TODO), and
	// the jitter fields need to be set once per frame by the not-yet-written jitter mixin and read
	// by EntityPatcher.patchMotionVectors's injected GLSL uniforms.
	private Matrix4fc currentEntityPreviousModelMatrix;
	private Matrix4fc previousGbufferModelView;
	private Matrix4fc previousGbufferProjection;
	private float currentJitterX;
	private float currentJitterY;
	private float previousJitterX;
	private float previousJitterY;

	private CapturedRenderingState() {
	}

	/**
	 * TODO(FSR2): last frame's fully-interpolated model matrix for whichever entity/block-entity
	 * is currently being rendered (set by the entity-render-dispatcher mixin from
	 * {@link PreviousObjectTransforms}, read by EntityPatcher.patchMotionVectors's injected
	 * per-draw uniform). {@code null} means "no previous transform recorded" (treat as zero
	 * velocity) -- see PreviousObjectTransforms's class doc.
	 */
	public Matrix4fc getCurrentEntityPreviousModelMatrix() {
		return currentEntityPreviousModelMatrix;
	}

	public void setCurrentEntityPreviousModelMatrix(Matrix4fc previousModelMatrix) {
		this.currentEntityPreviousModelMatrix = previousModelMatrix;
	}

	public Matrix4fc getPreviousGbufferModelView() {
		return previousGbufferModelView;
	}

	public void setPreviousGbufferModelView(Matrix4fc matrix) {
		this.previousGbufferModelView = matrix == null ? null : new Matrix4f(matrix);
	}

	public Matrix4fc getPreviousGbufferProjection() {
		return previousGbufferProjection;
	}

	public void setPreviousGbufferProjection(Matrix4fc matrix) {
		this.previousGbufferProjection = matrix == null ? null : new Matrix4f(matrix);
	}

	/** TODO(FSR2): this frame's sub-pixel jitter offset, in NDC units. Set once per frame. */
	public float getCurrentJitterX() {
		return currentJitterX;
	}

	public float getCurrentJitterY() {
		return currentJitterY;
	}

	public void setCurrentJitter(float x, float y) {
		this.currentJitterX = x;
		this.currentJitterY = y;
	}

	/**
	 * TODO(FSR2): last frame's jitter offset -- motion vectors must subtract the jitter delta
	 * between frames, or FSR2 misreads camera-only jitter movement as real object motion.
	 */
	public float getPreviousJitterX() {
		return previousJitterX;
	}

	public float getPreviousJitterY() {
		return previousJitterY;
	}

	public void setPreviousJitter(float x, float y) {
		this.previousJitterX = x;
		this.previousJitterY = y;
	}

	public Matrix4fc getGbufferModelView() {
		return gbufferModelView;
	}

	public void setGbufferModelView(Matrix4fc gbufferModelView) {
		this.gbufferModelView = gbufferModelView;
	}

	public Matrix4fc getGbufferProjection() {
		return gbufferProjection;
	}

	public void setGbufferProjection(Matrix4f gbufferProjection) {
		this.gbufferProjection = new Matrix4f(gbufferProjection);
	}

	public Vector3d getFogColor() {
		if (Minecraft.getInstance().level == null || fogColor == null) {
			return ZERO_VECTOR_3d;
		}

		return fogColor;
	}

	public void setFogColor(float red, float green, float blue) {
		fogColor = new Vector3d(red, green, blue);
	}

	public float getFogDensity() {
		return fogDensity;
	}

	public void setFogDensity(float fogDensity) {
		this.fogDensity = fogDensity;
	}

	public float getTickDelta() {
		return tickDelta;
	}

	public void setTickDelta(float tickDelta) {
		this.tickDelta = tickDelta;
	}

	public float getRealTickDelta() {
		return realTickDelta;
	}

	public void setRealTickDelta(float tickDelta) {
		this.realTickDelta = tickDelta;
	}

	public void setCurrentBlockEntity(int entity) {
		this.currentRenderedBlockEntity = entity;
	}

	public int getCurrentRenderedBlockEntity() {
		return currentRenderedBlockEntity;
	}

	public void setCurrentEntity(int entity) {
		this.currentRenderedEntity = entity;
	}

	public int getCurrentRenderedEntity() {
		return currentRenderedEntity;
	}

	public int getCurrentRenderedItem() {
		return currentRenderedItem;
	}

	public void setCurrentRenderedItem(int item) {
		this.currentRenderedItem = item;
	}

	public float getCurrentAlphaTest() {
		return currentAlphaTest;
	}

	public void setCurrentAlphaTest(float alphaTest) {
		this.currentAlphaTest = alphaTest;
	}

	public float getDarknessLightFactor() {
		return darknessLightFactor;
	}

	public void setDarknessLightFactor(float factor) {
		darknessLightFactor = factor;
	}

	public float getCloudTime() {
		return this.cloudTime;
	}

	public void setCloudTime(float cloudTime) {
		this.cloudTime = cloudTime;
	}
}
