package io.github.foundationgames.automobility.entity;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface EntityWithInventory {
    boolean hasInventory(@Nullable Player player);

    void openInventory(Player player);
}
