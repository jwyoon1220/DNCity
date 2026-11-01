package net.irisshaders.iris.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TODO(FSR2): sub-pixel jittered rasterization for AGENTS.md's "Architecture: FSR2 temporal
 * upscaling". NOT WIRED UP -- this class is a signature-only scaffold; the {@code @Mixin}/
 * {@code @ModifyReturnValue} annotations below are illustrative of the intended shape, not
 * verified against the real 1.21.1 {@code GameRenderer.getProjectionMatrix} signature, and this
 * class is deliberately NOT registered in Iris's mixin config yet (adding it there before the
 * body is real would silently no-op at best, and at worst apply a broken/uninitialized jitter to
 * every frame).
 * <p>
 * Uses a plain {@code @Inject(at = @At("RETURN"), cancellable = true)} +
 * {@code CallbackInfoReturnable} rather than a MixinExtras {@code @ModifyReturnValue} -- this
 * codebase's Iris fork has no confirmed MixinExtras dependency (grepped: no other use of
 * {@code @ModifyReturnValue} anywhere in this submodule), so this scaffold sticks to the plain
 * Sponge Mixin injector kind already proven elsewhere here (e.g. {@link MixinTweakFarPlane}'s
 * {@code @Redirect} usage on this same class).
 * <p>
 * Design, per the research already done (see AGENTS.md and the plan this scaffold was generated
 * from):
 * <ul>
 *   <li>Jitter must move where triangles actually rasterize (not just what matrix value shaders
 *       read), so it has to land in the real projection matrix used for rendering -- but as
 *       narrowly as possible. Only apply it inside {@code renderLevel}'s scope, gated on
 *       {@code Fsr2Hook.isActive()} (not yet written), so Iris behaves identically to today when
 *       FSR2 is off.
 *   <li>Jitter offset itself: a low-discrepancy sequence (Halton 2,3 is the common FSR2 choice),
 *       advanced once per frame, translating the returned matrix by
 *       {@code (2*jitterX/viewportWidth, 2*jitterY/viewportHeight, 0)} in NDC.
 *   <li>Store both the current and previous frame's jitter offset in
 *       {@link net.irisshaders.iris.uniforms.CapturedRenderingState#setCurrentJitter} /
 *       {@link net.irisshaders.iris.uniforms.CapturedRenderingState#setPreviousJitter} (both
 *       already scaffolded there) so EntityPatcher.patchMotionVectors's injected GLSL can
 *       subtract the jitter delta between frames when computing per-object velocity.
 *   <li><b>Must NOT apply to:</b> GUI/HUD rendering (verify vanilla GUI actually uses a separate
 *       ortho projection path, not this method at all -- required verification step, not an
 *       assumption) and Sodium's frustum culling (locate Sodium's actual frustum-build call site
 *       first -- open research item; if it reads this same jittered matrix, culling will
 *       incorrectly drop chunks at jittered frame edges). Prefer restoring/serving an unjittered
 *       matrix to both rather than trying to make jitter globally safe.
 *   <li><b>Compat risk:</b> {@code GameRenderer} is already mixed into by {@link MixinGameRenderer}
 *       in this same package, plus TACZ's {@code com.tacz.guns.mixin.client.GameRendererMixin} and
 *       SuperbWarfare's {@code com.atsuishio.superbwarfare.mixins.GameRendererMixin} (both likely
 *       FOV/recoil adjustments for ADS) -- mixin injection ordering here determines whether jitter
 *       lands before or after their adjustments. This must be verified by hand once this class is
 *       implemented and registered, per AGENTS.md's TACZ/SuperbWarfare verification procedure.
 * </ul>
 */
@Mixin(GameRenderer.class)
public class MixinFsr2Jitter {
	@Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true)
	private void fsr2$applyJitter(double fov, CallbackInfoReturnable<Matrix4f> cir) {
		// TODO(FSR2): if not active (Fsr2Hook.isActive() -- not written yet) or not inside
		// renderLevel's scope, return without cancelling so cir.getReturnValue() (vanilla's
		// unmodified matrix) passes through untouched. Once active, translate cir.getReturnValue()
		// by the current jitter offset in NDC and cir.setReturnValue(...) the result. Left
		// unimplemented -- see this class's own doc comment for why it isn't registered yet.
		throw new UnsupportedOperationException("TODO(FSR2): MixinFsr2Jitter.fsr2$applyJitter not implemented -- see AGENTS.md 'Architecture: FSR2 temporal upscaling'; also not yet registered in Iris's mixin config, see this class's own doc comment");
	}
}
