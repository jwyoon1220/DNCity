package net.irisshaders.iris.pipeline;

/**
 * TODO(FSR2): NOT WIRED UP -- signature-only scaffold for AGENTS.md's "Architecture: FSR2
 * temporal upscaling" section's confirmed requirement that FSR2 must work even with no Iris
 * shaderpack loaded (bare Iris / {@code Iris.getCurrentPack().isEmpty()}).
 * <p>
 * A1-A5's plumbing (the motion-vector G-buffer target, the entity previous-transform cache and
 * its GLSL injection, jittered rasterization) only exists while <i>some</i>
 * {@link WorldRenderingPipeline} is active -- {@link VanillaRenderingPipeline} (used when no
 * shaderpack is selected) does not run gbuffer/composite programs at all, so none of that
 * machinery has anywhere to attach. The fix: DNCity bundles a minimal no-op Iris shaderpack
 * resource (see the "Bundled passthrough shaderpack" section below) and, when FSR2 is enabled and
 * {@code Iris.getCurrentPack().isEmpty()}, this loader force-selects it via
 * {@code Iris.getPipelineManager()}'s existing pipeline-preparation path (see
 * {@code Iris.preparePipeline}/{@code Iris.getPipelineManager().preparePipeline(...)}, already
 * called elsewhere in {@code Iris.java} on dimension change) so an {@link IrisRenderingPipeline}
 * is active instead of the vanilla one. When the user has their own real shaderpack loaded, this
 * loader does nothing -- that pack's own pipeline is used unmodified and DNCity's FSR2 pass layers
 * on top of its actual composited output (see FinalPassRenderer.renderFinalPass's TODO for the
 * exact injection point).
 * <p>
 * <b>Bundled passthrough shaderpack</b> (not authored in this scaffold -- described here so
 * whoever implements it knows the exact contract): a shaderpack directory/zip with the minimum
 * viable {@code shaders.properties} plus pass-through GLSL for each program stage Iris requires
 * (gbuffers_terrain, gbuffers_entities, gbuffers_block, gbuffers_hand*, composite, final) that
 * each just forward vanilla's own per-fragment output unchanged -- i.e. visually identical to no
 * shaderpack at all, its only purpose being to make {@link IrisRenderingPipeline}'s gbuffer/
 * composite/final program pipeline exist so A1-A5 have somewhere to attach. Package this as a
 * DNCity mod resource (not an Iris resource), loaded via whatever mechanism Iris exposes for
 * installing/selecting a shaderpack from an arbitrary source (confirm the exact API -- not
 * researched yet in this scaffold).
 * <p>
 * Not implemented: this class currently has no fields or logic, only this design note, since the
 * actual force-load call requires confirming {@link PipelineManager}'s real API surface for
 * pack selection (as opposed to just dimension-triggered pipeline preparation) before writing
 * real code here.
 */
public final class Fsr2PassthroughPipelineLoader {
	private Fsr2PassthroughPipelineLoader() {
	}

	/**
	 * TODO(FSR2): called when FSR2 is toggled on (or on pipeline/dimension change while FSR2 is
	 * already on) to force-select the bundled passthrough pack if, and only if, the user has no
	 * real shaderpack of their own currently active.
	 */
	public static void ensurePipelineAvailable() {
		throw new UnsupportedOperationException("TODO(FSR2): Fsr2PassthroughPipelineLoader.ensurePipelineAvailable not implemented -- see AGENTS.md 'Architecture: FSR2 temporal upscaling'");
	}
}
