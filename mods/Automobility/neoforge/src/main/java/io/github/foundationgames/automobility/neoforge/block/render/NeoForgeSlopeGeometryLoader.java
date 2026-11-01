package io.github.foundationgames.automobility.neoforge.block.render;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.block.model.SlopeUnbakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.CustomLoaderBuilder;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.function.Function;

public enum NeoForgeSlopeGeometryLoader implements IGeometryLoader<NeoForgeSlopeGeometryLoader.LoadedSlopeGeometry> {
    INSTANCE;

    public static final ResourceLocation ID = Automobility.rl("slope");

    @Override
    public LoadedSlopeGeometry read(JsonObject jsonObject, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return new LoadedSlopeGeometry(SlopeUnbakedModel.DEFAULT_MODELS.get(ResourceLocation.parse(jsonObject.get("represents").getAsString())).get());
    }

    public record LoadedSlopeGeometry(SlopeUnbakedModel model) implements IUnbakedGeometry<LoadedSlopeGeometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext iGeometryBakingContext, ModelBaker modelBaker, Function<Material, TextureAtlasSprite> function, ModelState modelState, ItemOverrides itemOverrides) {
            return model().bake(modelBaker, function, modelState);
        }
    }

    public static class Builder<T extends ModelBuilder<T>> extends CustomLoaderBuilder<T> {
        private final ResourceLocation slopeModelId;

        public Builder(T parent, ExistingFileHelper existingFileHelper, ResourceLocation slopeModelId) {
            super(ID, parent, existingFileHelper, true);
            this.slopeModelId = slopeModelId;
        }

        @Override
        public JsonObject toJson(JsonObject json) {
            var obj = super.toJson(json);
            obj.addProperty("represents", slopeModelId.toString());
            return obj;
        }
    }
}
