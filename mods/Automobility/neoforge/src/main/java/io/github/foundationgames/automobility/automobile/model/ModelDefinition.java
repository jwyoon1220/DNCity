package io.github.foundationgames.automobility.automobile.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;

import java.util.function.Function;

public record ModelDefinition(ModelType type,
                              RenderMaterial material,
                              ModelLayerLocation modelLayer,
                              Vector3f translation,
                              Vector3f rotation,
                              Vector3f scale
) {
    public static final Codec<ModelLayerLocation> LAYER_CODEC = ResourceLocation.CODEC.xmap(rl -> {
        int layerStart = rl.getPath().lastIndexOf("/");
        var modelPath = rl.getPath().substring(0, layerStart);
        var layerPath = rl.getPath().substring(layerStart + 1);
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), modelPath), layerPath);
    }, ml -> ResourceLocation.fromNamespaceAndPath(ml.getModel().getNamespace(), ml.getModel().getPath() + "/" + ml.getLayer()));

    public static final Codec<ModelDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ModelType.CODEC.fieldOf("type").forGetter(ModelDefinition::type),
            RenderMaterial.CODEC.fieldOf("material").forGetter(ModelDefinition::material),
            LAYER_CODEC.fieldOf("model_layer").forGetter(ModelDefinition::modelLayer),
            ExtraCodecs.VECTOR3F.optionalFieldOf("translation", new Vector3f()).forGetter(ModelDefinition::translation),
            ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(ModelDefinition::rotation),
            ExtraCodecs.VECTOR3F.optionalFieldOf("scale", new Vector3f(1)).forGetter(ModelDefinition::scale)
    ).apply(inst, ModelDefinition::new));

    public static ModelDefinition of(ModelType type, RenderMaterial material, ModelLayerLocation modelLayer) {
        return ofYaw(type, material, modelLayer, 0);
    }

    public static ModelDefinition ofYaw(ModelType type, RenderMaterial material, ModelLayerLocation modelLayer, float yaw) {
        return new ModelDefinition(type, material, modelLayer, new Vector3f(), new Vector3f(0, yaw, 0), new Vector3f(1));
    }

    public static ModelDefinition ofScale(ModelType type, RenderMaterial material, ModelLayerLocation modelLayer, float scale) {
        return new ModelDefinition(type, material, modelLayer, new Vector3f(), new Vector3f(0, 0, 0), new Vector3f(scale));
    }

    public Model createModel(EntityRendererProvider.Context ctx) {
        return this.type().provider().create(ctx, material(), modelLayer(), translation(), rotation(), scale());
    }

    public enum RenderMaterial implements StringRepresentable {
        SOLID("solid", RenderType::entitySolid),
        CUTOUT("cutout", RenderType::entityCutout),
        CUTOUT_NO_CULL("cutout_backfaces", RenderType::entityCutoutNoCull),
        TRANSLUCENT("translucent", RenderType::entityTranslucentCull),
        TRANSLUCENT_NO_CULL("translucent_backfaces", RenderType::entityTranslucent),
        ADDITIVE_TRANSLUCENT("additive_translucent", RenderType::eyes),
        EMISSIVE("emissive", RenderType::breezeEyes);

        public static final Codec<RenderMaterial> CODEC = StringRepresentable.fromEnum(RenderMaterial::values);

        public final String id;
        public final Function<ResourceLocation, RenderType> renderType;

        RenderMaterial(String id, Function<ResourceLocation, RenderType> renderType) {
            this.id = id;
            this.renderType = renderType;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
