package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    // Vanilla natural regeneration calls Player#heal from inside FoodData#tick; the heal event handler needs to tell
    // that apart from other healing without capturing a stack trace on every heal.
    @Inject(method = "tick", at = @At("HEAD"))
    private void firstaid$enterFoodTick(Player player, CallbackInfo ci) {
        HealthDistribution.IN_FOOD_TICK.set(Boolean.TRUE);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void firstaid$leaveFoodTick(Player player, CallbackInfo ci) {
        HealthDistribution.IN_FOOD_TICK.set(Boolean.FALSE);
    }

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private boolean firstaid$gateNaturalRegen(boolean naturalRegen, Player player) {
        return naturalRegen && HealthDistribution.canApplyNaturalRegen(player);
    }
}
