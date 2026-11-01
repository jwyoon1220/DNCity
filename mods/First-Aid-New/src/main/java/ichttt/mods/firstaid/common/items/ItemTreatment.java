// AGENT-DONE(claude): tarkov-health-engine
package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A healing item that, besides restoring hit points, treats a specific injury (bleed, fracture) when the treatment of a
 * part completes, and that decides on its own which parts it can be applied to.
 */
public class ItemTreatment extends ItemHealing {
    @FunctionalInterface
    public interface Eligibility {
        /**
         * @param missingHealth whether the part is missing hit points (what a plain healing item looks at)
         */
        boolean test(AbstractDamageablePart part, boolean missingHealth);
    }

    private final Function<ItemStack, AbstractPartHealer> healerFunction;
    private final IntSupplier applyTimeMillis;
    private final Eligibility eligibility;
    private final Consumer<PartHealingContext> completion;
    private final Supplier<SoundEvent> useSound;
    @Nullable
    private final String tooltipKey;

    public ItemTreatment(Properties properties,
                         Function<ItemStack, AbstractPartHealer> healerFunction,
                         IntSupplier applyTimeMillis,
                         Eligibility eligibility,
                         Consumer<PartHealingContext> completion,
                         Supplier<SoundEvent> useSound,
                         @Nullable String tooltipKey) {
        super(properties, healerFunction, stack -> applyTimeMillis.getAsInt());
        this.healerFunction = healerFunction;
        this.applyTimeMillis = applyTimeMillis;
        this.eligibility = eligibility;
        this.completion = completion;
        this.useSound = useSound;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public AbstractPartHealer createNewHealer(ItemStack stack) {
        return healerFunction.apply(stack);
    }

    @Override
    public int getApplyTime(ItemStack stack) {
        return applyTimeMillis.getAsInt();
    }

    @Override
    public boolean canTreat(AbstractDamageablePart part) {
        return eligibility.test(part, super.canTreat(part));
    }

    @Nullable
    @Override
    public SoundEvent getApplySoundEvent(ItemStack stack) {
        return useSound.get();
    }

    @Override
    public ApplySoundMode getApplySoundMode(ItemStack stack) {
        return ApplySoundMode.WHILE_USING;
    }

    @Override
    public void onTreatmentCompleted(PartHealingContext context) {
        if (!context.getLevel().isClientSide()) {
            completion.accept(context);
        }
    }

    /** True if this item shows its own description instead of the generic "restores X hearts" line. */
    public boolean hasCustomTooltip() {
        return tooltipKey != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (tooltipKey != null) {
            tooltipComponents.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        }
    }
}
