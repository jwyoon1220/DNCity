package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Previously forced a flat corpse orientation while downed.
 * Downed players now rely on forced {@code Pose.SWIMMING} so vanilla crawl/swim
 * body orientation and movement animation are preserved.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> {
    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void firstaid$setupRotations(T entity, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale, CallbackInfo ci) {
        // Intentionally empty: do not cancel vanilla swimming/crawl rotations.
    }
}
