package io.github.foundationgames.automobility.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Shadow
    public Input input;

    @Shadow @Final protected Minecraft minecraft;

    @Inject(method = "rideTick", at = @At("TAIL"))
    public void automobility$setAutomobileInputs(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (self.getVehicle() instanceof AutomobileEntity vehicle && vehicle.isDriving(self)) {
            if (Platform.get().controller().inControllerMode() && minecraft.screen == null) {
                vehicle.provideClientInput(
                        Platform.get().controller().accelerating(),
                        Platform.get().controller().braking(),
                        input.left,
                        input.right,
                        Platform.get().controller().drifting(),
                        automobility$isSprinting()
                );
            } else {
                vehicle.provideClientInput(
                        input.up,
                        input.down,
                        input.left,
                        input.right,
                        input.jumping,
                        automobility$isSprinting()
                );
            }
        }
    }

    @Unique
    private boolean automobility$isSprinting() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), ((KeyMappingAccess) minecraft.options.keySprint).automobility$getKey().getValue());
    }
}
