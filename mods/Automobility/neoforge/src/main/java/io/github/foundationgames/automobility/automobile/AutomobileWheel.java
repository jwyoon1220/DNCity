package io.github.foundationgames.automobility.automobile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.util.DefaultRegistrar;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public record AutomobileWheel(
        boolean empty,
        float size,
        float grip,
        WheelModel model
) implements AutomobileComponent<AutomobileWheel> {
    public static final ResourceLocation ID = Automobility.rl("wheel");

    public static final ResourceKey<Registry<AutomobileWheel>> REGISTRY = ResourceKey.createRegistryKey(Automobility.rl("automobile_wheel"));
    public static final DefaultRegistrar<AutomobileWheel> BOOTSTRAP = new DefaultRegistrar<>(REGISTRY);

    public static final Codec<AutomobileWheel> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("_empty", false).forGetter(AutomobileWheel::empty),
            Codec.FLOAT.fieldOf("size").forGetter(AutomobileWheel::size),
            Codec.FLOAT.fieldOf("grip").forGetter(AutomobileWheel::grip),
            WheelModel.CODEC.fieldOf("display").forGetter(AutomobileWheel::model)
    ).apply(inst, AutomobileWheel::new));
    public static final Codec<ResourceKey<AutomobileWheel>> CODEC = ResourceKey.codec(REGISTRY);

    public static final StreamCodec<RegistryFriendlyByteBuf, AutomobileWheel> DIRECT_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AutomobileWheel::empty,
            ByteBufCodecs.FLOAT, AutomobileWheel::size,
            ByteBufCodecs.FLOAT, AutomobileWheel::grip,
            WheelModel.STREAM_CODEC, AutomobileWheel::model,
            AutomobileWheel::new
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<AutomobileWheel>> STREAM_CODEC = ByteBufCodecs.holder(REGISTRY, DIRECT_STREAM_CODEC);
    public static final EntityDataSerializer<Holder<AutomobileWheel>> SERIALIZER = EntityDataSerializer.forValueType(STREAM_CODEC);

    public static final AutomobileWheel EMPTY = new AutomobileWheel(true, 0.01f, 0.01f, new WheelModel(1, 1, ResourceLocation.parse("empty"), Automobility.rl("empty")));

    public static final ResourceKey<AutomobileWheel> EMPTY_KEY = BOOTSTRAP.register(Automobility.rl("empty"), EMPTY);

    public static AutomobileWheel of(float size, float grip, WheelModel model) {
        return new AutomobileWheel(false, size, grip, model);
    }

    public static final ResourceKey<AutomobileWheel> STANDARD = BOOTSTRAP.register(Automobility.rl("standard"),
            of(0.6f, 0.5f, new WheelModel(3, 3, Automobility.rl("textures/entity/automobile/wheel/standard.png"), Automobility.rl("wheel/standard")))
    );

    public static final ResourceKey<AutomobileWheel> OFF_ROAD = BOOTSTRAP.register(Automobility.rl("off_road"),
            of(1.1f, 0.8f, new WheelModel(8.4f, 5, Automobility.rl("textures/entity/automobile/wheel/off_road.png"), Automobility.rl("wheel/off_road")))
    );

    public static final ResourceKey<AutomobileWheel> STEEL = BOOTSTRAP.register(Automobility.rl("steel"),
            of(0.69f, 0.4f, new WheelModel(3.625f, 3, Automobility.rl("textures/entity/automobile/wheel/steel.png"), Automobility.rl("wheel/steel")))
    );

    public static final ResourceKey<AutomobileWheel> TRACTOR = BOOTSTRAP.register(Automobility.rl("tractor"),
            of(1.05f, 0.69f, new WheelModel(3.625f, 3, Automobility.rl("textures/entity/automobile/wheel/tractor.png"), Automobility.rl("wheel/tractor")))
    );

    public static final ResourceKey<AutomobileWheel> CARRIAGE = carriage("carriage", 0.2f);
    public static final ResourceKey<AutomobileWheel> PLATED = carriage("plated", 0.33f);
    public static final ResourceKey<AutomobileWheel> STREET = carriage("street", 0.5f);
    public static final ResourceKey<AutomobileWheel> GILDED = carriage("gilded", 0.45f);
    public static final ResourceKey<AutomobileWheel> BEJEWELED = carriage("bejeweled", 0.475f);

    private static ResourceKey<AutomobileWheel> carriage(String name, float grip) {
        return BOOTSTRAP.register(Automobility.rl(name),
                of(1.05f, grip, new WheelModel(5, 2, Automobility.rl("textures/entity/automobile/wheel/"+name+".png"), Automobility.rl("wheel/carriage"))));
    }

    public static final DisplayStat<AutomobileWheel> STAT_SIZE = new DisplayStat<>("size", AutomobileWheel::size);
    public static final DisplayStat<AutomobileWheel> STAT_GRIP = new DisplayStat<>("grip", AutomobileWheel::grip);

    @Override
    public boolean isEmpty() {
        return empty();
    }

    @Override
    public ResourceLocation containerId() {
        return ID;
    }

    @Override
    public void forEachStat(Consumer<DisplayStat<AutomobileWheel>> action) {
        action.accept(STAT_SIZE);
        action.accept(STAT_GRIP);
    }

    @Override
    public ResourceLocation getId() {
        return Automobility.rl("invalid");
    }

    public static String getTranslationKey(ResourceLocation id) {
        return "wheel."+id.getNamespace()+"."+id.getPath();
    }

    public record WheelModel(
        float radius,
        float width,
        ResourceLocation texture,
        ResourceLocation modelId
    ) {
        public static final Codec<WheelModel> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.FLOAT.fieldOf("radius").forGetter(WheelModel::radius),
                Codec.FLOAT.fieldOf("width").forGetter(WheelModel::width),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(WheelModel::texture),
                ResourceLocation.CODEC.fieldOf("model").forGetter(WheelModel::modelId)
        ).apply(inst, WheelModel::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, WheelModel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, WheelModel::radius,
                ByteBufCodecs.FLOAT, WheelModel::width,
                ResourceLocation.STREAM_CODEC, WheelModel::texture,
                ResourceLocation.STREAM_CODEC, WheelModel::modelId,
                WheelModel::new
        );
    }
}
