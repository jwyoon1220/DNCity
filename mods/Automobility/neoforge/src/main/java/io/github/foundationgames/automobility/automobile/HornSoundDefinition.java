package io.github.foundationgames.automobility.automobile;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.sound.AutomobilitySounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.NoteBlock;

import java.util.List;
import java.util.function.Supplier;

public record HornSoundDefinition(Supplier<SoundEvent> sound, float loopStart, float loopEnd, List<Float> pitches) {
    public static final HornSoundDefinition DEFAULT = klaxon(12);

    public static final Codec<HornSoundDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            SoundEvent.DIRECT_CODEC.fieldOf("sound").forGetter(e -> e.sound().get()),
            Codec.FLOAT.fieldOf("loop_start").forGetter(HornSoundDefinition::loopStart),
            Codec.FLOAT.fieldOf("loop_end").forGetter(HornSoundDefinition::loopEnd),
            Codec.list(Codec.FLOAT).fieldOf("pitches").forGetter(HornSoundDefinition::pitches)
    ).apply(inst, HornSoundDefinition::create));

    public static final StreamCodec<RegistryFriendlyByteBuf, HornSoundDefinition> STREAM_CODEC = StreamCodec.composite(
            SoundEvent.DIRECT_STREAM_CODEC, d -> d.sound().get(),
            ByteBufCodecs.FLOAT, HornSoundDefinition::loopStart,
            ByteBufCodecs.FLOAT, HornSoundDefinition::loopEnd,
            ByteBufCodecs.<ByteBuf, Float>list().apply(ByteBufCodecs.FLOAT), HornSoundDefinition::pitches,
            HornSoundDefinition::create
    );

    public static HornSoundDefinition create(SoundEvent sound, float loopStart, float loopEnd, List<Float> pitches) {
        return new HornSoundDefinition(() -> sound, loopStart, loopEnd, pitches);
    }

    public static HornSoundDefinition klaxon(int ... notes) {
        return new HornSoundDefinition(AutomobilitySounds.KLAXON_HORN::require, 0.1916f, 0.9405f, pitchesOf(notes));
    }

    public static HornSoundDefinition disc(int ... notes) {
        return new HornSoundDefinition(AutomobilitySounds.DISC_HORN::require, 0.09435f, 0.6605f, pitchesOf(notes));
    }

    public static HornSoundDefinition trumpet(int ... notes) {
        return new HornSoundDefinition(AutomobilitySounds.TRUMPET_HORN::require, 0.151f, 0.95f, pitchesOf(notes));
    }

    public static HornSoundDefinition brass(int ... notes) {
        return new HornSoundDefinition(AutomobilitySounds.BRASS_HORN::require, 0.2019f, 0.3138f, pitchesOf(notes));
    }

    public static HornSoundDefinition party(int ... notes) {
        return new HornSoundDefinition(AutomobilitySounds.PARTY_HORN::require, -1, -1, pitchesOf(notes));
    }

    public static List<Float> pitchesOf(int ... notes) {
        var b = ImmutableList.<Float>builder();
        for (int i : notes) {
            b.add(NoteBlock.getPitchFromNote(i));
        }
        return b.build();
    }
}
