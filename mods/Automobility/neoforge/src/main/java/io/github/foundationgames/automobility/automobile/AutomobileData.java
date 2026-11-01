package io.github.foundationgames.automobility.automobile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.Optional;
import java.util.function.Consumer;

public record AutomobileData(Optional<ResourceLocation> prefabName,
                             ResourceKey<AutomobileFrame> frame,
                             ResourceKey<AutomobileWheel> wheel,
                             ResourceKey<AutomobileEngine> engine) implements TooltipProvider {
    public static final Codec<AutomobileData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.optionalFieldOf("prefab_id").forGetter(AutomobileData::prefabName),
            AutomobileFrame.CODEC.fieldOf("frame").forGetter(AutomobileData::frame),
            AutomobileWheel.CODEC.fieldOf("wheels").forGetter(AutomobileData::wheel),
            AutomobileEngine.CODEC.fieldOf("engine").forGetter(AutomobileData::engine)
    ).apply(inst, AutomobileData::new));

    private static final AutomobileStats stats = new AutomobileStats();

    public static AutomobileData prefab(ResourceLocation prefabName,
                                        ResourceKey<AutomobileFrame> frame,
                                        ResourceKey<AutomobileWheel> wheel,
                                        ResourceKey<AutomobileEngine> engine) {
        return new AutomobileData(Optional.of(prefabName), frame, wheel, engine);
    }

    public ItemStack asStack() {
        var stack = AutomobilityItems.AUTOMOBILE.require().getDefaultInstance();
        stack.set(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require(), this);

        if (this.prefabName().isPresent()) {
            var name = this.prefabName().get();
            stack.set(DataComponents.ITEM_NAME, Component.translatable(String.format(
                    "prefab.%s.%s", name.getNamespace(), name.getPath()
            )));
        }

        return stack;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        var frame = context.registries().lookupOrThrow(AutomobileFrame.REGISTRY).get(frame());
        var wheel = context.registries().lookupOrThrow(AutomobileWheel.REGISTRY).get(wheel());
        var engine = context.registries().lookupOrThrow(AutomobileEngine.REGISTRY).get(engine());

        if (Screen.hasShiftDown()) {
            stats.from(
                    frame.map(Holder.Reference::value).orElse(AutomobileFrame.EMPTY),
                    wheel.map(Holder.Reference::value).orElse(AutomobileWheel.EMPTY),
                    engine.map(Holder.Reference::value).orElse(AutomobileEngine.EMPTY)
            );
            stats.appendTexts(tooltip, stats);
        } else {
            if (prefabName().isEmpty()) {
                tooltip.accept(
                        Component.translatable("tooltip.automobility.frameLabel").withStyle(ChatFormatting.BLUE)
                                .append(Component.translatable(AutomobileFrame.getTranslationKey(frame().location())).withStyle(ChatFormatting.DARK_GREEN))
                );
                tooltip.accept(
                        Component.translatable("tooltip.automobility.wheelLabel").withStyle(ChatFormatting.BLUE)
                                .append(Component.translatable(AutomobileWheel.getTranslationKey(wheel().location())).withStyle(ChatFormatting.DARK_GREEN))
                );
                tooltip.accept(
                        Component.translatable("tooltip.automobility.engineLabel").withStyle(ChatFormatting.BLUE)
                                .append(Component.translatable(AutomobileEngine.getTranslationKey(engine().location())).withStyle(ChatFormatting.DARK_GREEN))
                );
            }
            tooltip.accept(Component.translatable("tooltip.automobility.shiftForStats").withStyle(ChatFormatting.GOLD));
        }
    }
}
