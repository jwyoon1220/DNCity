package io.github.foundationgames.automobility.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface EntityWithContainer extends Container {
    Container underlyingContainer();

    @Override
    default int getContainerSize() {
        var c = underlyingContainer();
        if (c != null) {
            return c.getContainerSize();
        }

        return 0;
    }

    @Override
    default boolean isEmpty() {
        var c = underlyingContainer();
        if (c != null) {
            return c.isEmpty();
        }

        return true;
    }

    @Override
    default ItemStack getItem(int slot) {
        var c = underlyingContainer();
        if (c != null) {
            return c.getItem(slot);
        }

        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItem(int slot, int amount) {
        var c = underlyingContainer();
        if (c != null) {
            return c.removeItem(slot, amount);
        }

        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        var c = underlyingContainer();
        if (c != null) {
            return c.removeItemNoUpdate(slot);
        }

        return ItemStack.EMPTY;
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
        var c = underlyingContainer();
        if (c != null) {
            c.setItem(slot, stack);
        }
    }

    @Override
    default void setChanged() {
        var c = underlyingContainer();
        if (c != null) {
            c.setChanged();
        }
    }

    @Override
    default boolean stillValid(Player player) {
        var c = underlyingContainer();
        if (c != null) {
            return c.stillValid(player);
        }

        return false;
    }

    @Override
    default void clearContent() {
        var c = underlyingContainer();
        if (c != null) {
            c.clearContent();
        }
    }

    @Override
    default boolean canPlaceItem(int slot, ItemStack stack) {
        var c = underlyingContainer();
        if (c != null) {
            return c.canPlaceItem(slot, stack);
        }

        return false;
    }

    @Override
    default boolean canTakeItem(Container target, int slot, ItemStack stack) {
        var c = underlyingContainer();
        if (c != null) {
            return c.canTakeItem(target, slot, stack);
        }

        return false;
    }
}
