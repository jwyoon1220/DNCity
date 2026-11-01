// AGENT-DONE(claude): tarkov-health-engine
package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.health.VanillaHealthBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla health of a player is derived from its limb model. Anything that tries to write it directly is translated
 * into limb damage/healing by {@link VanillaHealthBridge} instead of being stored.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthMixin {
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void firstaid$routeSetHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && VanillaHealthBridge.onExternalSetHealth(player, health)) {
            ci.cancel();
        }
    }
}
