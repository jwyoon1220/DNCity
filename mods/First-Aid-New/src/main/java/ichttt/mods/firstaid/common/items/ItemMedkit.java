// AGENT-DONE(claude): realistic-wound-care
package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PartHealer;
import ichttt.mods.firstaid.common.health.HealthUnits;
import ichttt.mods.firstaid.common.health.InjuryEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A medkit with a resource pool (Tarkov-style): its durability is the pool, in limb hit points. One application on a part first
 * pays for closing that part's bleed and, if the kit can, setting its fracture, then for the hit points it restores; it takes only
 * what the part needs and what is left in the kit. No medkit works on a destroyed limb (that needs surgery).
 * <p>
 * Costs of -1 mean the kit cannot treat that injury. All numbers are game design.
 */
public class ItemMedkit extends ItemHealing {
    /** What one application will do, decided from the part and the resource left. */
    public record Plan(byte closeBleed, boolean fixFracture, int healHp) {
        public int cost(ItemMedkit kit) {
            int c = healHp;
            if (closeBleed == AbstractDamageablePart.BLEED_LIGHT) c += kit.lightBleedCost;
            if (closeBleed == AbstractDamageablePart.BLEED_HEAVY) c += kit.heavyBleedCost;
            if (fixFracture) c += kit.fractureCost;
            return c;
        }
    }

    /** The healer of one application; remembers what it has to close when it starts. */
    public static class MedkitHealer extends PartHealer {
        public final Plan plan;

        public MedkitHealer(Plan plan, int ticksPerHeal, int heals, ItemStack stack) {
            super(() -> ticksPerHeal, () -> heals, stack);
            this.plan = plan;
        }
    }

    private final int applyTimeMillis;
    private final int secondsPerPulse;
    private final int lightBleedCost;
    private final int heavyBleedCost;
    private final int fractureCost;

    public ItemMedkit(Properties properties, int applyTimeMillis, int secondsPerPulse, int lightBleedCost, int heavyBleedCost, int fractureCost) {
        super(properties, stack -> null, stack -> applyTimeMillis);
        this.applyTimeMillis = applyTimeMillis;
        this.secondsPerPulse = secondsPerPulse;
        this.lightBleedCost = lightBleedCost;
        this.heavyBleedCost = heavyBleedCost;
        this.fractureCost = fractureCost;
    }

    public static int remaining(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private boolean treatsBleed(byte level) {
        return (level == AbstractDamageablePart.BLEED_LIGHT && lightBleedCost >= 0) || (level == AbstractDamageablePart.BLEED_HEAVY && heavyBleedCost >= 0);
    }

    /** Bleed first, then the fracture, then hit points, each only if the resource left pays for it. Pure. */
    public Plan plan(AbstractDamageablePart part, int available) {
        if (part.isDestroyed() || available <= 0) {
            return new Plan(AbstractDamageablePart.BLEED_NONE, false, 0);
        }
        int left = available;
        byte bleed = AbstractDamageablePart.BLEED_NONE;
        if (treatsBleed(part.bleedLevel)) {
            int c = part.bleedLevel == AbstractDamageablePart.BLEED_HEAVY ? heavyBleedCost : lightBleedCost;
            if (c <= left) {
                bleed = part.bleedLevel;
                left -= c;
            }
        }
        boolean fracture = false;
        if (part.fractured && fractureCost >= 0 && fractureCost <= left) {
            fracture = true;
            left -= fractureCost;
        }
        int missing = (int) Math.ceil(Math.max(0.0F, part.effectiveMaxHealth() - part.currentHealth) - 0.01F);
        return new Plan(bleed, fracture, Math.max(0, Math.min(left, missing)));
    }

    @Override
    public boolean canTreat(AbstractDamageablePart part) {
        if (part.isDestroyed()) {
            return false;
        }
        return super.canTreat(part) || treatsBleed(part.bleedLevel) || (part.fractured && fractureCost >= 0);
    }

    /** The healer for one application on {@code part}; heal pulses cover the planned hit points. */
    public AbstractPartHealer createHealer(ItemStack stack, Plan plan, Player player, @Nullable AbstractPlayerDamageModel model) {
        float pulse = Math.max(0.01F, HealthUnits.healPulse(player, model));
        int heals = (int) Math.ceil(plan.healHp() / pulse);
        return new MedkitHealer(plan, FirstAid.scaleMedicalTimingTicks(secondsPerPulse * 20), heals, stack);
    }

    @Override
    public AbstractPartHealer createNewHealer(ItemStack stack) {
        // Used where there is no part to plan for (reloading a healer from NBT, treating someone else): a short generic course
        return new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(secondsPerPulse * 20), () -> 2, stack);
    }

    @Override
    public int getApplyTime(ItemStack stack) {
        return applyTimeMillis;
    }

    @Override
    public void onTreatmentStarted(PartHealingContext context) {
        if (context.getLevel().isClientSide() || !(context.getHealer() instanceof MedkitHealer healer)) {
            return;
        }
        AbstractDamageablePart part = context.getDamageablePart();
        boolean changed = false;
        if (healer.plan.closeBleed() != AbstractDamageablePart.BLEED_NONE) {
            changed |= InjuryEngine.stopBleed(part, healer.plan.closeBleed());
        }
        if (healer.plan.fixFracture()) {
            changed |= InjuryEngine.fixFracture(part);
        }
        if (changed) {
            context.getDamageModel().scheduleResync();
        }
    }

    @Nullable
    @Override
    public SoundEvent getApplySoundEvent(ItemStack stack) {
        return RegistryObjects.BANDAGE_USE.value();
    }

    @Override
    public ApplySoundMode getApplySoundMode(ItemStack stack) {
        return ApplySoundMode.WHILE_USING;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("firstaid.tooltip.medkit.resource", remaining(stack), stack.getMaxDamage()).withStyle(ChatFormatting.GREEN));
        if (lightBleedCost >= 0) {
            tooltip.add(Component.translatable("firstaid.tooltip.medkit.light_bleed", lightBleedCost).withStyle(ChatFormatting.GRAY));
        }
        if (heavyBleedCost >= 0) {
            tooltip.add(Component.translatable("firstaid.tooltip.medkit.heavy_bleed", heavyBleedCost).withStyle(ChatFormatting.GRAY));
        }
        if (fractureCost >= 0) {
            tooltip.add(Component.translatable("firstaid.tooltip.medkit.fracture", fractureCost).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("firstaid.tooltip.medkit.no_destroyed").withStyle(ChatFormatting.DARK_GRAY));
    }
}
