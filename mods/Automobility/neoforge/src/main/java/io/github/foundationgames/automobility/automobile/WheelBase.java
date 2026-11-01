package io.github.foundationgames.automobility.automobile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record WheelBase(List<WheelPos> wheels) {
    public static final Codec<WheelBase> CODEC = Codec.list(WheelPos.CODEC).xmap(WheelBase::new, WheelBase::wheels);

    public static final StreamCodec<RegistryFriendlyByteBuf, WheelBase> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<RegistryFriendlyByteBuf, WheelPos>list().apply(WheelPos.STREAM_CODEC), WheelBase::wheels,
            WheelBase::new
    );

    public int wheelCount() {
        return wheels().size();
    }

    public double longLength() {
        double min = wheels.stream().map(WheelPos::forward).min(Comparator.comparingDouble(f -> (double) f)).orElse(0f);
        double max = wheels.stream().map(WheelPos::forward).max(Comparator.comparingDouble(f -> (double) f)).orElse(0f);

        return (max - min) / 16;
    }

    public enum WheelSide implements StringRepresentable {
        LEFT,
        CENTER,
        RIGHT;

        public static final Codec<WheelSide> CODEC = StringRepresentable.fromEnum(WheelSide::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum WheelEnd implements StringRepresentable {
        FRONT,
        BACK,
        NONE;

        public static final Codec<WheelEnd> CODEC = StringRepresentable.fromEnum(WheelEnd::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record WheelPos(float forward, float right, float scale, float yaw, WheelEnd end, WheelSide side) {
        public static final Codec<WheelPos> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.FLOAT.fieldOf("forward").forGetter(WheelPos::forward),
                Codec.FLOAT.fieldOf("right").forGetter(WheelPos::right),
                Codec.FLOAT.fieldOf("scale").forGetter(WheelPos::scale),
                Codec.FLOAT.fieldOf("yaw").forGetter(WheelPos::yaw),
                WheelEnd.CODEC.fieldOf("end").forGetter(WheelPos::end),
                WheelSide.CODEC.fieldOf("side").forGetter(WheelPos::side)
        ).apply(inst, WheelPos::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, WheelPos> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, WheelPos::forward,
                ByteBufCodecs.FLOAT, WheelPos::right,
                ByteBufCodecs.FLOAT, WheelPos::scale,
                ByteBufCodecs.FLOAT, WheelPos::yaw,
                ByteBufCodecs.INT, p -> p.end().ordinal(),
                ByteBufCodecs.INT, p -> p.side().ordinal(),
                (a,b,c,d,e,f) -> new WheelPos(a, b, c, d, WheelEnd.values()[e], WheelSide.values()[f])
        );
    }

    public static WheelBase basic(float sepLong, float sepWide) {
        return new WheelBase((List.of(
                new WheelPos(sepLong / 2, sepWide / -2, 1, 0, WheelEnd.FRONT, WheelSide.LEFT),
                new WheelPos(sepLong / -2, sepWide / -2, 1, 0, WheelEnd.BACK, WheelSide.LEFT),
                new WheelPos(sepLong / 2, sepWide / 2, 1, 180, WheelEnd.FRONT, WheelSide.RIGHT),
                new WheelPos(sepLong / -2, sepWide / 2, 1, 180, WheelEnd.BACK, WheelSide.RIGHT)
        )));
    }
}
