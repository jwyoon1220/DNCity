package io.github.foundationgames.automobility.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class DefaultRegistrar<V> {
    public final ResourceKey<Registry<V>> registryKey;
    private final Map<ResourceKey<V>, V> toRegister = new HashMap<>();

    public DefaultRegistrar(ResourceKey<Registry<V>> registry) {
        this.registryKey = registry;
    }

    public ResourceKey<V> register(ResourceLocation id, V value) {
        var key = ResourceKey.create(this.registryKey, id);
        toRegister.put(key, value);
        return key;
    }

    public ResourceKey<V> register(Candidate<V> c) {
        return register(c.id(), c.value());
    }

    public void bootstrap(RegistrationContext<V> registry) {
        for (var e : toRegister.entrySet()) {
            registry.register(e.getKey(), e.getValue());
        }
    }

    public static <V> Candidate<V> cand(ResourceLocation id, V value) {
        return new Candidate<>(id, value);
    }

    public record Candidate<V>(ResourceLocation id, V value) {
    }

    public interface RegistrationContext<V> {
        void register(ResourceKey<V> key, V value);
    }
}