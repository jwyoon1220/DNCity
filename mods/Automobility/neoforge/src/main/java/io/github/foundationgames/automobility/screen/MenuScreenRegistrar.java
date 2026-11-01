package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.util.Eventual;
import io.github.foundationgames.automobility.util.TriFunc;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public interface MenuScreenRegistrar {
    <T extends AbstractContainerMenu, U extends Screen & MenuAccess<T>> void accept(Eventual<MenuType<T>> type, TriFunc<T, Inventory, Component, U> factory);
}
