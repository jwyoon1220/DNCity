package io.github.foundationgames.automobility.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.foundationgames.automobility.platform.Platform;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutomobilityClientResourceDumper {
    public static final Path DUMP_DIR = Platform.get().getGameDir().resolve("automobility_dump");
    public static final Gson GSON = new Gson();

    public static <R> void dumpDynamicRegistry(HolderLookup.Provider registries, ResourceKey<Registry<R>> key, Codec<R> codec) throws IOException {
        var registryMaybe = registries.lookup(key);

        if (registryMaybe.isPresent()) {
            var registry = registryMaybe.get();
            var regId = key.location();
            var dumpRoot = "data";
            var subFolder = regId.getNamespace() + "/" + regId.getPath();

            for (var e : registry.listElements().toList()) {
                var location = e.key().location();

                dumpJsonResource(dumpRoot, subFolder, location, e.value(), codec);
            }
        }
    }

    public static <R> void dumpJsonResource(String root, String subfolder, ResourceLocation location,
                                            R resource, Codec<R> codec) throws IOException {
        var file = DUMP_DIR.resolve(root).resolve(location.getNamespace()).resolve(subfolder).resolve(location.getPath() + ".json");
        var folder = file.getParent();

        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }

        var result = codec.encode(resource, JsonOps.INSTANCE, new JsonObject());

        if (result.isSuccess()) {
            var json = result.getOrThrow();

            try (var writer = GSON.newJsonWriter(Files.newBufferedWriter(file))) {
                writer.setIndent("    ");
                GSON.toJson(json, writer);
            }
        }
    }
}
