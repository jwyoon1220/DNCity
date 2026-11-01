package io.github.foundationgames.automobility.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.neoforge.client.BEWLRs;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityWithoutLevelRenderer.class)
public class BlockEntityWithoutLevelRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void automobility$renderBEWLRS(ItemStack stack, ItemDisplayContext displayContext, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (BEWLRs.tryRender(stack, displayContext, pose, buffers, light, overlay)) {
            ci.cancel();
        }
    }
}
