package ichttt.mods.firstaid.client.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public final class HeartSpriteHelper {
    private HeartSpriteHelper() {
    }

    public static ResourceLocation container(Player player, boolean blinking) {
        String name = player.level().getLevelData().isHardcore() ? "container_hardcore" : "container";
        return sprite(name + (blinking ? "_blinking" : ""));
    }

    public static ResourceLocation heart(Player player, boolean absorbing, boolean half, boolean blinking) {
        boolean hardcore = player.level().getLevelData().isHardcore();
        String type;
        if (absorbing) type = "absorbing";
        else if (player.hasEffect(MobEffects.POISON)) type = "poisoned";
        else if (player.hasEffect(MobEffects.WITHER)) type = "withered";
        else if (player.isFullyFrozen()) type = "frozen";
        else type = "";

        StringBuilder name = new StringBuilder();
        if (!type.isEmpty()) name.append(type).append('_');
        if (hardcore) name.append("hardcore_");
        name.append(half ? "half" : "full");
        if (blinking) name.append("_blinking");
        return sprite(name.toString());
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.withDefaultNamespace("hud/heart/" + name);
    }
}
