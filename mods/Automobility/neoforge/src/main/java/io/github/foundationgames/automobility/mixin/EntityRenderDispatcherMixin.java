package io.github.foundationgames.automobility.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Unique
    private boolean automobility$displacedRotation = false;

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    shift = At.Shift.BEFORE,
                    ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void automobility$rotateEntitiesOnAutomobile(Entity entity, double x, double y, double z, float rotationYaw, float tickDelta, PoseStack pose, MultiBufferSource buffer, int packedLight, CallbackInfo info) {
        var vehicle = entity.getVehicle();

        if (vehicle instanceof AutomobileEntity auto) {
            pose.pushPose();

            double attHeightOffset = entity.getEyeHeight();
            pose.translate(0, attHeightOffset, 0);

            var rotation = new Quaternionf();
            auto.getDisplacement().getAngular(tickDelta, rotation);

            pose.mulPose(rotation);
            pose.translate(0, -attHeightOffset, 0);

            automobility$displacedRotation = true;
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    shift = At.Shift.AFTER,
                    ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void automobility$undoTransform(Entity entity, double x, double y, double z, float rotationYaw, float tickDelta, PoseStack pose, MultiBufferSource buffer, int packedLight, CallbackInfo info) {
        if (automobility$displacedRotation) {
            automobility$displacedRotation = false;
            pose.popPose();
        }
    }
}
