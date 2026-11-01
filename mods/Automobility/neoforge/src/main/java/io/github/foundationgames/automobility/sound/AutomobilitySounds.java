package io.github.foundationgames.automobility.sound;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.util.Eventual;
import io.github.foundationgames.automobility.util.RegistryQueue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public class AutomobilitySounds {
    public static final Eventual<SoundEvent> COLLISION = register("entity.automobile.collision");
    public static final Eventual<SoundEvent> LANDING = register("entity.automobile.landing");
    public static final Eventual<SoundEvent> SKID = register("entity.automobile.skid");

    public static final Eventual<SoundEvent> STONE_ENGINE = register("entity.automobile.stone_engine");
    public static final Eventual<SoundEvent> COPPER_ENGINE = register("entity.automobile.copper_engine");
    public static final Eventual<SoundEvent> IRON_ENGINE = register("entity.automobile.iron_engine");
    public static final Eventual<SoundEvent> GOLD_ENGINE = register("entity.automobile.gold_engine");
    public static final Eventual<SoundEvent> DIAMOND_ENGINE = register("entity.automobile.diamond_engine");
    public static final Eventual<SoundEvent> CREATIVE_ENGINE = register("entity.automobile.creative_engine");

    public static final Eventual<SoundEvent> KLAXON_HORN = register("entity.automobile.klaxon_horn");
    public static final Eventual<SoundEvent> DISC_HORN = register("entity.automobile.disc_horn");
    public static final Eventual<SoundEvent> TRUMPET_HORN = register("entity.automobile.trumpet_horn");
    public static final Eventual<SoundEvent> BRASS_HORN = register("entity.automobile.brass_horn");
    public static final Eventual<SoundEvent> PARTY_HORN = register("entity.automobile.party_horn");

    private static Eventual<SoundEvent> register(String path) {
        var id = Automobility.rl(path);
        return RegistryQueue.register(BuiltInRegistries.SOUND_EVENT, id, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}
