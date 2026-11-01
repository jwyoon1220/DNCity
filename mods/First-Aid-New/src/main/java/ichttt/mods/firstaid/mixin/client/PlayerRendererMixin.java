package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Face-down crawl orientation while downed (not face-up corpse roll).
 * XP -90 pitches the body into the ground like vanilla swimming/crawl.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void firstaid$setupRotations(AbstractClientPlayer entity, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (!RenderStateExtensions.shouldApplyUnconsciousAttributes(entity)) {
            return;
        }

        float collapseProgress = RenderStateExtensions.getCollapseProgress(entity);
        // Face-down crawl: pitch into the ground, small settle only (large Y bury the head).
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F * collapseProgress));
        poseStack.translate(0.0D, -0.15D * collapseProgress, 0.20D * collapseProgress);
        ci.cancel();
    }
}
