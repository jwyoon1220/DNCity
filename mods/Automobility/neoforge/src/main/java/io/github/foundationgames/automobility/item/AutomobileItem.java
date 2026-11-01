package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.AutomobilityEntities;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutomobileItem extends Item implements CustomCreativeOutput {
    public static final List<AutomobileData> PREFABS = new ArrayList<>();

    public AutomobileItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            var stack = context.getItemInHand();
            var data = stack.get(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require());
            var e = new AutomobileEntity(AutomobilityEntities.AUTOMOBILE.require(), context.getLevel());
            var pos = context.getClickLocation();
            e.moveTo(pos.x, pos.y, pos.z, context.getHorizontalDirection().toYRot(), 0);

            var frame = context.getLevel().registryAccess().registryOrThrow(AutomobileFrame.REGISTRY).getHolder(data.frame())
                    .map(r -> (Holder<AutomobileFrame>)r).orElseGet(() -> Holder.direct(AutomobileFrame.EMPTY));
            var wheel = context.getLevel().registryAccess().registryOrThrow(AutomobileWheel.REGISTRY).getHolder(data.wheel())
                    .map(r -> (Holder<AutomobileWheel>)r).orElseGet(() -> Holder.direct(AutomobileWheel.EMPTY));
            var engine = context.getLevel().registryAccess().registryOrThrow(AutomobileEngine.REGISTRY).getHolder(data.engine())
                    .map(r -> (Holder<AutomobileEngine>)r).orElseGet(() -> Holder.direct(AutomobileEngine.EMPTY));
            e.setComponents(frame, wheel, engine);

            context.getLevel().addFreshEntity(e);
            stack.shrink(1);
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    public static void addPrefabs(AutomobileData ... prefabs) {
        PREFABS.addAll(Arrays.asList(prefabs));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        var data = stack.get(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require());

        if (data != null) {
            data.addToTooltip(context, tooltipComponents::add, tooltipFlag);
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public void provideCreativeOutput(CreativeModeTab.Output output, HolderLookup.Provider registries) {
        for (var prefab : PREFABS) {
            output.accept(prefab.asStack());
        }
    }
}
