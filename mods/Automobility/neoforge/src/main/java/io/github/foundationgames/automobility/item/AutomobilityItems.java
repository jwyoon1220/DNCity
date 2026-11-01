package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.util.Eventual;
import io.github.foundationgames.automobility.util.RegistryQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public enum AutomobilityItems {;
    public static final Eventual<Item> CROWBAR = register("crowbar", () -> new TooltipItem(Component.translatable("tooltip.item.automobility.crowbar").withStyle(ChatFormatting.BLUE), new Item.Properties().stacksTo(1)), Automobility.TAB);
    public static final Eventual<Item> AUTOMOBILE = register("automobile", () -> new AutomobileItem(new Item.Properties().stacksTo(1)), Automobility.PREFAB_TAB);
    public static final Eventual<AutomobileFrameItem> AUTOMOBILE_FRAME = register("automobile_frame", () -> new AutomobileFrameItem(new Item.Properties().stacksTo(16)), Automobility.TAB);
    public static final Eventual<AutomobileWheelItem> AUTOMOBILE_WHEEL = register("automobile_wheel", () -> new AutomobileWheelItem(new Item.Properties()), Automobility.TAB);
    public static final Eventual<AutomobileEngineItem> AUTOMOBILE_ENGINE = register("automobile_engine", () -> new AutomobileEngineItem(new Item.Properties().stacksTo(16)), Automobility.TAB);
    public static final Eventual<FrontAttachmentItem> FRONT_ATTACHMENT = register("front_attachment", () -> new FrontAttachmentItem(new Item.Properties().stacksTo(1)), Automobility.TAB);
    public static final Eventual<RearAttachmentItem> REAR_ATTACHMENT = register("rear_attachment", () -> new RearAttachmentItem(new Item.Properties().stacksTo(1)), Automobility.TAB);

    public static final Eventual<DataComponentType<AutomobileData>> COMPONENT_AUTOMOBILE_DATA = RegistryQueue.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, Automobility.rl("automobile_data"),
            () -> DataComponentType.<AutomobileData>builder().persistent(AutomobileData.CODEC).build());

    public static final Eventual<DataComponentType<ResourceLocation>> COMPONENT_GENERIC_AUTO_PART = RegistryQueue.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, Automobility.rl("automobile_part"),
            () -> DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC).build());

    public static final Eventual<DataComponentType<ResourceKey<AutomobileFrame>>> COMPONENT_FRAME = RegistryQueue.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, AutomobileFrame.ID,
            () -> DataComponentType.<ResourceKey<AutomobileFrame>>builder().persistent(AutomobileFrame.CODEC).build());
    public static final Eventual<DataComponentType<ResourceKey<AutomobileWheel>>> COMPONENT_WHEEL = RegistryQueue.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, AutomobileWheel.ID,
            () -> DataComponentType.<ResourceKey<AutomobileWheel>>builder().persistent(AutomobileWheel.CODEC).build());
    public static final Eventual<DataComponentType<ResourceKey<AutomobileEngine>>> COMPONENT_ENGINE = RegistryQueue.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, AutomobileEngine.ID,
            () -> DataComponentType.<ResourceKey<AutomobileEngine>>builder().persistent(AutomobileEngine.CODEC).build());

    public static void init() {
        AutomobileItem.addPrefabs(
                AutomobileData.prefab(Automobility.rl("wooden_motorcar"), AutomobileFrame.WOODEN_MOTORCAR, AutomobileWheel.CARRIAGE, AutomobileEngine.STONE),
                AutomobileData.prefab(Automobility.rl("copper_motorcar"), AutomobileFrame.COPPER_MOTORCAR, AutomobileWheel.PLATED, AutomobileEngine.COPPER),
                AutomobileData.prefab(Automobility.rl("steel_motorcar"), AutomobileFrame.STEEL_MOTORCAR, AutomobileWheel.STREET, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("golden_motorcar"), AutomobileFrame.GOLDEN_MOTORCAR, AutomobileWheel.GILDED, AutomobileEngine.GOLD),
                AutomobileData.prefab(Automobility.rl("bejeweled_motorcar"), AutomobileFrame.BEJEWELED_MOTORCAR, AutomobileWheel.BEJEWELED, AutomobileEngine.DIAMOND),
                AutomobileData.prefab(Automobility.rl("standard_white"), AutomobileFrame.STANDARD_WHITE, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_orange"), AutomobileFrame.STANDARD_ORANGE, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_magenta"), AutomobileFrame.STANDARD_MAGENTA, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_light_blue"), AutomobileFrame.STANDARD_LIGHT_BLUE, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_yellow"), AutomobileFrame.STANDARD_YELLOW, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_lime"), AutomobileFrame.STANDARD_LIME, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_pink"), AutomobileFrame.STANDARD_PINK, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_gray"), AutomobileFrame.STANDARD_GRAY, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_light_gray"), AutomobileFrame.STANDARD_LIGHT_GRAY, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_cyan"), AutomobileFrame.STANDARD_CYAN, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_purple"), AutomobileFrame.STANDARD_PURPLE, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_blue"), AutomobileFrame.STANDARD_BLUE, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_brown"), AutomobileFrame.STANDARD_BROWN, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_green"), AutomobileFrame.STANDARD_GREEN, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_red"), AutomobileFrame.STANDARD_RED, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("standard_black"), AutomobileFrame.STANDARD_BLACK, AutomobileWheel.STANDARD, AutomobileEngine.IRON),
                AutomobileData.prefab(Automobility.rl("amethyst_rickshaw"), AutomobileFrame.AMETHYST_RICKSHAW, AutomobileWheel.BEJEWELED, AutomobileEngine.STONE),
                AutomobileData.prefab(Automobility.rl("quartz_rickshaw"), AutomobileFrame.QUARTZ_RICKSHAW, AutomobileWheel.GILDED, AutomobileEngine.GOLD),
                AutomobileData.prefab(Automobility.rl("prismarine_rickshaw"), AutomobileFrame.PRISMARINE_RICKSHAW, AutomobileWheel.PLATED, AutomobileEngine.COPPER),
                AutomobileData.prefab(Automobility.rl("echo_rickshaw"), AutomobileFrame.ECHO_RICKSHAW, AutomobileWheel.STREET, AutomobileEngine.DIAMOND),
                AutomobileData.prefab(Automobility.rl("red_tractor"), AutomobileFrame.RED_TRACTOR, AutomobileWheel.TRACTOR, AutomobileEngine.COPPER),
                AutomobileData.prefab(Automobility.rl("yellow_tractor"), AutomobileFrame.YELLOW_TRACTOR, AutomobileWheel.TRACTOR, AutomobileEngine.COPPER),
                AutomobileData.prefab(Automobility.rl("green_tractor"), AutomobileFrame.GREEN_TRACTOR, AutomobileWheel.TRACTOR, AutomobileEngine.COPPER),
                AutomobileData.prefab(Automobility.rl("blue_tractor"), AutomobileFrame.BLUE_TRACTOR, AutomobileWheel.TRACTOR, AutomobileEngine.COPPER),
                AutomobileData.prefab(Automobility.rl("shopping_cart"), AutomobileFrame.SHOPPING_CART, AutomobileWheel.STEEL, AutomobileEngine.STONE),
                AutomobileData.prefab(Automobility.rl("c_arr"), AutomobileFrame.C_ARR, AutomobileWheel.OFF_ROAD, AutomobileEngine.DIAMOND),
                AutomobileData.prefab(Automobility.rl("pineapple"), AutomobileFrame.PINEAPPLE, AutomobileWheel.TRACTOR, AutomobileEngine.GOLD)
        );
    }

    public static <T extends Item> Eventual<T> register(String name, Supplier<T> item, CreativeTabQueue tab) {
        var itemPromise = RegistryQueue.register(BuiltInRegistries.ITEM, Automobility.rl(name), item);
        tab.queue(itemPromise);
        return itemPromise;
    }
}
