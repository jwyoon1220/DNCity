package io.github.foundationgames.automobility.recipe;

import io.github.foundationgames.automobility.Automobility;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AutoMechanicTableRecipe implements Recipe<ContainerRecipeInput>, Comparable<AutoMechanicTableRecipe> {
    public static final ResourceLocation ID = Automobility.rl("auto_mechanic_table");
    public static final RecipeType<AutoMechanicTableRecipe> TYPE = new RecipeType<>() {};

    protected final ResourceLocation category;
    protected final List<Ingredient> ingredients;
    protected final ItemStack result;
    protected final int sortNum;

    public @Nullable ResourceLocation sortId;

    public AutoMechanicTableRecipe(ResourceLocation category, List<Ingredient> ingredients, ItemStack result, int sortNum) {
        this.category = category;
        this.ingredients = ingredients;
        this.result = result;
        this.sortNum = sortNum;
    }

    public ResourceLocation getCategory() {
        return this.category;
    }

    @Override
    public boolean matches(ContainerRecipeInput inv, Level lvl) {
        boolean[] result = {true};
        this.forMissingIngredients(inv, ing -> result[0] = false);

        return result[0];
    }

    @Override
    public ItemStack assemble(ContainerRecipeInput inv, HolderLookup.Provider var2) {
        return assemble(inv);
    }

    public ItemStack assemble(ContainerRecipeInput inv) {
        for (var ing : this.ingredients) {
            for (int i = 0; i < inv.size(); i++) {
                var stack = inv.getItem(i);
                if (ing.test(stack)) {
                    stack.shrink(1);
                    break;
                }
            }
        }

        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider var1) {
        return getResultItem();
    }

    public ItemStack getResultItem() {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AutoMechanicTableRecipeSerializer.INSTANCE;
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    public void forMissingIngredients(ContainerRecipeInput inv, Consumer<Ingredient> action) {
        var invCopy = new ArrayList<ItemStack>();
        for (int i = 0; i < inv.size(); i++) {
            invCopy.add(inv.getItem(i));
        }

        for (var ing : this.ingredients) {
            if (invCopy.stream().noneMatch(ing)) {
                action.accept(ing);
            } else {
                invCopy.remove(invCopy.stream().filter(ing).collect(Collectors.toList()).get(0));
            }
        }
    }

    @Override
    public int compareTo(@NotNull AutoMechanicTableRecipe o) {
        int diff = this.getCategory().compareTo(o.getCategory());
        if (diff != 0) return diff;

        diff = Integer.compare(this.sortNum, o.sortNum);
        if (diff != 0) return diff;

        if (this.sortId != null && o.sortId != null) {
            return this.sortId.compareTo(o.sortId);
        }

        return this.getResultItem().getItemHolder().getRegisteredName()
                .compareTo(o.getResultItem().getItemHolder().getRegisteredName());
    }
}
