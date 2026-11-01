package io.github.foundationgames.automobility.neoforge.vendored.jsonem.util;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import io.github.foundationgames.automobility.neoforge.vendored.jsonem.JsonEM;
import io.github.foundationgames.automobility.neoforge.vendored.jsonem.serialization.JsonEMCodecs;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.ClientHooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;

public final class JsonEntityModelUtil {
    public static final Gson GSON = new Gson();

    private JsonEntityModelUtil() {}

    public static Optional<LayerDefinition> readJson(InputStream data) {
        JsonElement json = GSON.fromJson(GSON.newJsonReader(new InputStreamReader(data)), JsonObject.class);

        return JsonEMCodecs.LAYER_DEFINITION.decode(JsonOps.INSTANCE, json).result().map(Pair::getFirst);
    }

    public static void loadModels(ResourceManager manager, Map<ModelLayerLocation, LayerDefinition> layers) {
        var tempMap = new ImmutableMap.Builder<ModelLayerLocation, LayerDefinition>();
        ClientHooks.loadLayerDefinitions(tempMap);

        FileToIdConverter.json("models/entity").listMatchingResources(manager).forEach((id, res) -> {
            try {
                var fullPath = id.getPath().replaceFirst("models/entity/", "");
                var splitPath = fullPath.split("/");
                var dirs = new String[splitPath.length - 1];
                System.arraycopy(splitPath, 0, dirs, 0, dirs.length);
                var layerName = splitPath[splitPath.length - 1].replace(".json", "");
                var modelName = String.join("/", dirs);
                var layer = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), modelName), layerName);

                try (var in = res.open()) {
                    var data = readJson(in);
                    data.ifPresent(model -> layers.put(layer, model));
                }
            } catch (ResourceLocationException | IOException ex) {
                JsonEM.LOG.error(ex);
            }
        });
    }
}
