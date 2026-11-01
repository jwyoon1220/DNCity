package io.github.foundationgames.automobility.recipe;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record ContainerRecipeInput(SimpleContainer container) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return container().getItem(index);
    }

    @Override
    public int size() {
        return container().getContainerSize();
    }
}
