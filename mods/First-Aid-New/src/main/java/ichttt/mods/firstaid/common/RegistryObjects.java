/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import ichttt.mods.firstaid.common.health.InjuryEngine;
import ichttt.mods.firstaid.common.items.ItemMedkit;
import ichttt.mods.firstaid.common.items.ItemTreatment;
import ichttt.mods.firstaid.common.damagesystem.PartHealer;
import ichttt.mods.firstaid.common.items.ItemAdrenalineInjector;
import ichttt.mods.firstaid.common.items.ItemMorphine;
import ichttt.mods.firstaid.common.items.ItemMorphineInjector;
import ichttt.mods.firstaid.common.items.ItemPainkillers;
import ichttt.mods.firstaid.common.potion.FirstAidPotion;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RegistryObjects {
    private static final DeferredRegister.Items ITEM_REGISTER = DeferredRegister.createItems(FirstAid.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENT_REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, FirstAid.MODID);
    private static final DeferredRegister<MobEffect> MOB_EFFECT_REGISTER = DeferredRegister.create(Registries.MOB_EFFECT, FirstAid.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FirstAid.MODID);

    public static final DeferredItem<ItemHealing> BANDAGE;
    public static final DeferredItem<ItemHealing> PLASTER;
    public static final DeferredItem<ItemHealing> TOURNIQUET;
    public static final DeferredItem<ItemHealing> SPLINT;
    public static final DeferredItem<ItemHealing> TRAUMA_KIT;
    // Tarkov-level medical items (marker realistic-wound-care). All counts, costs and times are game design.
    public static final DeferredItem<ItemHealing> CAR_FIRST_AID_KIT;
    public static final DeferredItem<ItemHealing> IFAK;
    public static final DeferredItem<ItemHealing> AFAK;
    public static final DeferredItem<ItemHealing> FIELD_MEDKIT;
    public static final DeferredItem<ItemHealing> CAT_TOURNIQUET;
    public static final DeferredItem<ItemHealing> HEMOSTATIC_GAUZE;
    public static final DeferredItem<ItemHealing> ALUMINUM_SPLINT;
    public static final DeferredItem<ItemHealing> SUTURE_KIT;
    public static final DeferredItem<ItemHealing> SURGICAL_KIT;
    public static final DeferredItem<ItemHealing> FIELD_SURGICAL_KIT;
    public static final DeferredItem<Item> DEFIBRILLATOR;
    public static final DeferredItem<ItemAdrenalineInjector> ADRENALINE_INJECTOR;
    public static final DeferredItem<ItemMorphineInjector> MORPHINE_INJECTOR;
    public static final DeferredItem<ItemMorphine> MORPHINE;
    public static final DeferredItem<ItemPainkillers> PAINKILLERS;

    public static final Holder<SoundEvent> HEARTBEAT;
    public static final Holder<SoundEvent> TINNITUS;
    public static final Holder<SoundEvent> BANDAGE_USE;
    public static final Holder<SoundEvent> DEFIBRILLATOR_USE;
    public static final Holder<SoundEvent> ADRENALINE_INJECTOR_USE;
    public static final Holder<SoundEvent> PILLS_USE;

    public static final Holder<MobEffect> MORPHINE_EFFECT;
    public static final Holder<MobEffect> PAINKILLER_EFFECT;
    public static final Holder<MobEffect> ADDICTION_INCREASE_EFFECT;
    public static final Holder<MobEffect> ADDICTION_ULTRA_INCREASE_EFFECT;
    public static final Holder<MobEffect> ADDICTION_DECREASE_EFFECT;
    public static final Holder<MobEffect> ADDICTION_WITHDRAWAL_EFFECT;
    public static final Holder<MobEffect> POISON_PATCHED;

    public static final Holder<CreativeModeTab> CREATIVE_TAB;

    static {
        FirstAidConfig.Server server = FirstAidConfig.SERVER;

        // ITEMS
        // Bandage and plaster restore hit points and, once the treatment is done, close a light bleed on that part.
        BANDAGE = ITEM_REGISTER.registerItem("bandage", properties -> new ItemTreatment(
                properties.stacksTo(16),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.bandage.secondsPerHeal.get() * 20), server.bandage.totalHeals::get, stack),
                server.bandage.applyTime::get,
                (part, missingHealth) -> missingHealth || part.bleedLevel == AbstractDamageablePart.BLEED_LIGHT || lightTourniquet(part),
                context -> dress(context, AbstractDamageablePart.BLEED_LIGHT),
                RegistryObjects::getBandageUseSound,
                null
        ));
        PLASTER = ITEM_REGISTER.registerItem("plaster", properties -> new ItemTreatment(
                properties.stacksTo(16),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.plaster.secondsPerHeal.get() * 20), server.plaster.totalHeals::get, stack),
                server.plaster.applyTime::get,
                (part, missingHealth) -> missingHealth || part.bleedLevel == AbstractDamageablePart.BLEED_LIGHT || lightTourniquet(part),
                context -> dress(context, AbstractDamageablePart.BLEED_LIGHT),
                RegistryObjects::getBandageUseSound,
                null
        ));
        // A tourniquet only exists for limbs; it stops any bleed there at once but starts the ischemia clock (InjuryEngine).
        TOURNIQUET = ITEM_REGISTER.registerItem("tourniquet", properties -> new ItemTreatment(
                properties.stacksTo(8),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.tourniquet.secondsPerHeal.get() * 20), server.tourniquet.totalHeals::get, stack),
                server.tourniquet.applyTime::get,
                (part, missingHealth) -> InjuryEngine.isLimb(part.part) && part.bleedLevel != AbstractDamageablePart.BLEED_NONE,
                RegistryObjects::tieTourniquet,
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.tourniquet"
        ));
        SPLINT = ITEM_REGISTER.registerItem("splint", properties -> new ItemTreatment(
                properties.stacksTo(8),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.splint.secondsPerHeal.get() * 20), server.splint.totalHeals::get, stack),
                server.splint.applyTime::get,
                (part, missingHealth) -> part.fractured,
                context -> {
                    if (InjuryEngine.fixFracture(context.getDamageablePart())) {
                        context.getDamageModel().scheduleResync();
                    }
                },
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.splint"
        ));
        // Heals the chosen part quickly and closes every bleed on it, including the heavy bleeds of head and body.
        TRAUMA_KIT = ITEM_REGISTER.registerItem("trauma_kit", properties -> new ItemTreatment(
                properties.stacksTo(4),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.traumaKit.secondsPerHeal.get() * 20), server.traumaKit.totalHeals::get, stack),
                server.traumaKit.applyTime::get,
                (part, missingHealth) -> missingHealth || part.bleedLevel != AbstractDamageablePart.BLEED_NONE || part.hasTourniquet(),
                context -> dress(context, AbstractDamageablePart.BLEED_HEAVY),
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.trauma_kit"
        ));
        DEFIBRILLATOR = ITEM_REGISTER.registerItem("defibrillator", properties -> new Item(properties.durability(3)));

        // Medkits: durability = resource pool in limb hit points (head 35, body 85 by default). Bleed/fracture costs are paid first.
        // ItemMedkit(properties, applyTimeMillis, secondsPerPulse, lightBleedCost, heavyBleedCost, fractureCost); -1 = cannot treat it.
        CAR_FIRST_AID_KIT = ITEM_REGISTER.registerItem("car_first_aid_kit", properties -> new ItemMedkit(properties.durability(220), 3000, 1, 50, -1, -1));
        IFAK = ITEM_REGISTER.registerItem("ifak", properties -> new ItemMedkit(properties.durability(300), 3000, 1, 30, -1, -1));
        AFAK = ITEM_REGISTER.registerItem("afak", properties -> new ItemMedkit(properties.durability(400), 3000, 1, 30, 170, -1));
        FIELD_MEDKIT = ITEM_REGISTER.registerItem("field_medkit", properties -> new ItemMedkit(properties.durability(1800), 5000, 1, 40, 130, 50));
        // CAT-style windlass tourniquet: one hand, faster than the improvised one, a few uses.
        CAT_TOURNIQUET = ITEM_REGISTER.registerItem("cat_tourniquet", properties -> new ItemTreatment(
                properties.durability(3),
                stack -> new PartHealer(() -> 1, () -> 1, stack),
                () -> 2000,
                (part, missingHealth) -> InjuryEngine.isLimb(part.part) && part.bleedLevel != AbstractDamageablePart.BLEED_NONE,
                RegistryObjects::tieTourniquet,
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.cat_tourniquet"
        ));
        // Hemostatic (combat) gauze: packs a heavy bleed anywhere, the junctional and torso wounds a tourniquet cannot reach, and
        // is the dressing that lets a tourniquet come off.
        HEMOSTATIC_GAUZE = ITEM_REGISTER.registerItem("hemostatic_gauze", properties -> new ItemTreatment(
                properties.durability(3),
                stack -> new PartHealer(() -> 1, () -> 1, stack),
                () -> 3000,
                (part, missingHealth) -> !part.isDestroyed() && (part.bleedLevel != AbstractDamageablePart.BLEED_NONE || part.hasTourniquet()),
                context -> dress(context, AbstractDamageablePart.BLEED_HEAVY),
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.hemostatic_gauze"
        ));
        ALUMINUM_SPLINT = ITEM_REGISTER.registerItem("aluminum_splint", properties -> new ItemTreatment(
                properties.durability(5),
                stack -> new PartHealer(() -> 1, () -> 1, stack),
                () -> 5000,
                (part, missingHealth) -> part.fractured && !part.isDestroyed(),
                context -> {
                    if (InjuryEngine.fixFracture(context.getDamageablePart())) {
                        context.getDamageModel().scheduleResync();
                    }
                },
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.aluminum_splint"
        ));
        // Closes dressed wounds for good (they cannot reopen any more) and takes a tourniquet off.
        SUTURE_KIT = ITEM_REGISTER.registerItem("suture_kit", properties -> new ItemTreatment(
                properties.durability(5),
                stack -> new PartHealer(() -> 1, () -> 1, stack),
                () -> 12000,
                (part, missingHealth) -> !part.isDestroyed() && part.bleedLevel == AbstractDamageablePart.BLEED_NONE
                        && (part.dressedWound != AbstractDamageablePart.BLEED_NONE || part.hasTourniquet()),
                context -> {
                    if (InjuryEngine.suture(context.getDamageablePart())) {
                        context.getDamageModel().scheduleResync();
                    }
                },
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.suture_kit"
        ));
        // Surgery on a destroyed limb: back to 1 hit point, the limb's maximum permanently reduced (until death).
        SURGICAL_KIT = ITEM_REGISTER.registerItem("surgical_kit", properties -> new ItemTreatment(
                properties.durability(5),
                stack -> new PartHealer(() -> 1, () -> 1, stack),
                () -> 16000,
                (part, missingHealth) -> part.isDestroyed(),
                context -> operate(context, 0.30F),
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.surgical_kit"
        ));
        FIELD_SURGICAL_KIT = ITEM_REGISTER.registerItem("field_surgical_kit", properties -> new ItemTreatment(
                properties.durability(15),
                stack -> new PartHealer(() -> 1, () -> 1, stack),
                () -> 16000,
                (part, missingHealth) -> part.isDestroyed(),
                context -> operate(context, 0.15F),
                RegistryObjects::getBandageUseSound,
                "firstaid.tooltip.field_surgical_kit"
        ));
        ADRENALINE_INJECTOR = ITEM_REGISTER.registerItem("adrenaline_injector", ItemAdrenalineInjector::new);
        MORPHINE_INJECTOR = ITEM_REGISTER.registerItem("morphine_injector", ItemMorphineInjector::new);
        MORPHINE = ITEM_REGISTER.registerItem("morphine", ItemMorphine::new);
        PAINKILLERS = ITEM_REGISTER.registerItem("painkillers", ItemPainkillers::new);

        // SOUNDS
        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "debuff.heartbeat");
        HEARTBEAT = SOUND_EVENT_REGISTER.register(soundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(soundLocation));
        ResourceLocation tinnitusSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "debuff.tinnitus");
        TINNITUS = SOUND_EVENT_REGISTER.register(tinnitusSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(tinnitusSoundLocation));
        ResourceLocation bandageSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.use_bandage");
        BANDAGE_USE = SOUND_EVENT_REGISTER.register(bandageSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(bandageSoundLocation));
        ResourceLocation defibrillatorSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.defibrillator_use");
        DEFIBRILLATOR_USE = SOUND_EVENT_REGISTER.register(
                defibrillatorSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(defibrillatorSoundLocation)
        );
        ResourceLocation adrenalineInjectorSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.adrenaline_injector_use");
        ADRENALINE_INJECTOR_USE = SOUND_EVENT_REGISTER.register(
                adrenalineInjectorSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(adrenalineInjectorSoundLocation)
        );
        ResourceLocation pillsSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.take_pills");
        PILLS_USE = SOUND_EVENT_REGISTER.register(pillsSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(pillsSoundLocation));

        // MOB EFFECTS
        MORPHINE_EFFECT = MOB_EFFECT_REGISTER.register("morphine", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0xDDD));
        PAINKILLER_EFFECT = MOB_EFFECT_REGISTER.register("painkiller", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0x6EC5FF));
        ADDICTION_INCREASE_EFFECT = MOB_EFFECT_REGISTER.register("addiction_increase", () -> new FirstAidPotion(MobEffectCategory.HARMFUL, 0xE8A0A0));
        ADDICTION_ULTRA_INCREASE_EFFECT = MOB_EFFECT_REGISTER.register("addiction_ultra_increase", () -> new FirstAidPotion(MobEffectCategory.HARMFUL, 0xFF6A6A));
        ADDICTION_DECREASE_EFFECT = MOB_EFFECT_REGISTER.register("addiction_decrease", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0x90C090));
        ADDICTION_WITHDRAWAL_EFFECT = MOB_EFFECT_REGISTER.register("addiction", () -> new FirstAidPotion(MobEffectCategory.HARMFUL, 0xC8A2C8));
        POISON_PATCHED = MobEffects.POISON;

        // CREATIVE MODE TABS
        CREATIVE_TAB = CREATIVE_MODE_TAB_REGISTER.register("main_tab", key -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.firstaid"))
                .icon(() -> new ItemStack(BANDAGE.get()))
                .build());
    }


    public static void registerToBus(IEventBus bus) {
        ITEM_REGISTER.register(bus);
        SOUND_EVENT_REGISTER.register(bus);
        MOB_EFFECT_REGISTER.register(bus);
        CREATIVE_MODE_TAB_REGISTER.register(bus);
    }

    private static SoundEvent getBandageUseSound() {
        return BANDAGE_USE.value();
    }

    /** Dresses a bleed up to {@code strongest} and, if the wound under a tourniquet is no worse than that, takes the tourniquet off. */
    private static void dress(PartHealingContext context, byte strongest) {
        AbstractDamageablePart part = context.getDamageablePart();
        boolean changed = InjuryEngine.stopBleed(part, strongest);
        changed |= InjuryEngine.convertTourniquet(part, strongest);
        if (changed) {
            context.getDamageModel().scheduleResync();
        }
    }

    private static boolean lightTourniquet(AbstractDamageablePart part) {
        return part.hasTourniquet() && part.dressedWound <= AbstractDamageablePart.BLEED_LIGHT;
    }

    private static void tieTourniquet(PartHealingContext context) {
        if (InjuryEngine.applyTourniquet(context.getDamageablePart())) {
            context.getDamageModel().scheduleResync();
        }
    }

    private static void operate(PartHealingContext context, float penalty) {
        if (InjuryEngine.surgery(context.getDamageablePart(), penalty)) {
            context.getDamageModel().scheduleResync();
            if (context.getPlayer() != null && context.getDamageModel() instanceof ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel model) {
                model.syncVanillaHealth(context.getPlayer());
            }
        }
    }
}



