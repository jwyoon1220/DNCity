// AGENT-DONE(claude): ammo-round-variants
package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.item.IAmmoRound;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Which rounds are in a gun, in firing order (chamber first, then magazine). TACZ itself only stores a count, so this is a
 * parallel record kept in the gun's custom data: rounds are appended when they are taken from the inventory
 * ({@link com.tacz.guns.api.item.gun.AbstractGunItem#findAndExtractInventoryAmmo}) and popped when a shot is fired.
 * <p>
 * The count stays the authority. Anything that loads rounds without taking items (creative, dummy ammo, scripts) leaves
 * the record shorter than the count; the missing rounds are "unknown" and fire with TACZ's default behaviour. Before new
 * rounds are appended the record is trimmed to what is really in the gun, so a drift never outlives one reload.
 * <p>
 * Stored run-length encoded: {@code LoadedRounds: [{Id: "dncity:cartridge_556x45_m855", Tracer: 0b, Count: 4}, ...]},
 * an empty {@code Id} is an unknown round (generic TACZ ammo, ammo box).
 */
public final class LoadedRounds {
    public static final String LOADED_ROUNDS_TAG = "LoadedRounds";
    public static final String FEED_CYCLE_TAG = "RoundFeedCycle";
    private static final String ID = "Id";
    private static final String TRACER = "Tracer";
    private static final String COUNT = "Count";

    /** One tracer in this many rounds when both kinds are available: 4 others, then 1 tracer. */
    public static final int TRACER_PERIOD = 5;

    /** A loaded round; {@code id == null} means unknown (fire with TACZ's default tracer interval). */
    public record Round(@Nullable ResourceLocation id, boolean tracer) {
        public static final Round UNKNOWN = new Round(null, false);

        public boolean known() {
            return id != null;
        }

        public static Round of(ItemStack ammo) {
            if (ammo.getItem() instanceof IAmmoRound round) {
                return new Round(BuiltInRegistries.ITEM.getKey(ammo.getItem()), round.isTracerRound(ammo));
            }
            return UNKNOWN;
        }
    }

    private LoadedRounds() {
    }

    public static List<Round> read(ItemStack gun) {
        CompoundTag nbt = gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        List<Round> rounds = new ArrayList<>();
        if (!nbt.contains(LOADED_ROUNDS_TAG, Tag.TAG_LIST)) {
            return rounds;
        }
        ListTag list = nbt.getList(LOADED_ROUNDS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String idText = entry.getString(ID);
            ResourceLocation id = idText.isEmpty() ? null : ResourceLocation.tryParse(idText);
            Round round = new Round(id, id != null && entry.getBoolean(TRACER));
            for (int n = Math.max(0, entry.getInt(COUNT)); n > 0; n--) {
                rounds.add(round);
            }
        }
        return rounds;
    }

    public static void write(ItemStack gun, List<Round> rounds) {
        ListTag list = new ListTag();
        Round last = null;
        CompoundTag lastTag = null;
        for (Round round : rounds) {
            if (round.equals(last) && lastTag != null) {
                lastTag.putInt(COUNT, lastTag.getInt(COUNT) + 1);
                continue;
            }
            lastTag = new CompoundTag();
            lastTag.putString(ID, round.id() == null ? "" : round.id().toString());
            lastTag.putBoolean(TRACER, round.tracer());
            lastTag.putInt(COUNT, 1);
            list.add(lastTag);
            last = round;
        }
        gun.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            if (list.isEmpty()) {
                tag.remove(LOADED_ROUNDS_TAG);
            } else {
                tag.put(LOADED_ROUNDS_TAG, list);
            }
        }));
    }

    /** Keeps the first {@code keep} rounds and returns the ones removed, in order. */
    public static List<Round> trim(ItemStack gun, int keep) {
        List<Round> rounds = read(gun);
        int k = Math.max(0, keep);
        if (rounds.size() <= k) {
            return List.of();
        }
        List<Round> removed = new ArrayList<>(rounds.subList(k, rounds.size()));
        write(gun, new ArrayList<>(rounds.subList(0, k)));
        return removed;
    }

    public static void append(ItemStack gun, List<Round> added) {
        if (added.isEmpty()) {
            return;
        }
        List<Round> rounds = read(gun);
        rounds.addAll(added);
        write(gun, rounds);
    }

    /** Removes and returns the next round to fire, {@link Round#UNKNOWN} if nothing is recorded. */
    public static Round pop(ItemStack gun) {
        List<Round> rounds = read(gun);
        if (rounds.isEmpty()) {
            return Round.UNKNOWN;
        }
        Round first = rounds.remove(0);
        write(gun, rounds);
        return first;
    }

    public static int getFeedCycle(ItemStack gun) {
        CompoundTag nbt = gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Math.floorMod(nbt.getInt(FEED_CYCLE_TAG), TRACER_PERIOD);
    }

    public static void setFeedCycle(ItemStack gun, int cycle) {
        int value = Math.floorMod(cycle, TRACER_PERIOD);
        gun.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putInt(FEED_CYCLE_TAG, value)));
    }

    /** Whether the round at this position of the feed cycle should be a tracer: positions 0..3 others, 4 a tracer. */
    public static boolean wantsTracer(int cycle) {
        return Math.floorMod(cycle, TRACER_PERIOD) == TRACER_PERIOD - 1;
    }
}
