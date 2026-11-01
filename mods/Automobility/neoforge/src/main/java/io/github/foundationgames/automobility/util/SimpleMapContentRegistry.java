package io.github.foundationgames.automobility.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class SimpleMapContentRegistry<V extends SimpleMapContentRegistry.Identifiable> {
    private final Map<ResourceLocation, V> entries = new Object2ObjectOpenHashMap<>();
    private final List<ResourceLocation> orderedKeys = new ArrayList<>();

    public SimpleMapContentRegistry() {
    }

    public V register(V entry) {
        entries.put(entry.getId(), entry);
        orderedKeys.add(entry.getId());
        return entry;
    }

    public V get(ResourceLocation name) {
        return entries.get(name);
    }

    public Optional<V> getOptional(ResourceLocation name) {
        return Optional.ofNullable(entries.get(name));
    }

    public V getOrDefault(ResourceLocation name) {
        if (orderedKeys.size() <= 0) throw new IllegalStateException("Tried to get from empty registry!");
        return entries.getOrDefault(name, entries.get(orderedKeys.get(0)));
    }

    public void forEach(Consumer<V> action) {
        orderedKeys.forEach(k -> action.accept(entries.get(k)));
    }

    public Codec<V> codec() {
        return ResourceLocation.CODEC.xmap(this::get, Identifiable::getId);
    }

    public interface Identifiable {
        ResourceLocation getId();
    }

    public static class Serializing<V extends SimpleMapContentRegistry.Identifiable> extends SimpleMapContentRegistry<V> implements Codec<V> {
        @Override
        public <T> DataResult<Pair<V, T>> decode(DynamicOps<T> ops, T input) {
            return ResourceLocation.CODEC.decode(ops, input).flatMap(pair ->
                    this.getOptional(pair.getFirst()).map(v -> DataResult.success(pair.mapFirst(id -> v)))
                            .orElseGet(() -> DataResult.error(() -> String.format("Entry '%s' does not exist", pair.getFirst()))));
        }

        @Override
        public <T> DataResult<T> encode(V input, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createString(input.getId().toString()));
        }
    }
}
