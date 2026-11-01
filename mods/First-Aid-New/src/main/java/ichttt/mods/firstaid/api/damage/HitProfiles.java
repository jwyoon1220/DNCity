// AGENT-DONE(claude): shell-ballistics
package ichttt.mods.firstaid.api.damage;

import net.minecraft.world.damagesource.DamageSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Hook for mods that know more about a hit than its damage type does. Providers are asked once per hit on a player,
 * the first non-null profile is used while that hit is applied.
 */
public final class HitProfiles {
    private static final List<Function<DamageSource, HitProfile>> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<HitProfile> CURRENT = new ThreadLocal<>();

    private HitProfiles() {
    }

    public static void register(Function<DamageSource, HitProfile> provider) {
        PROVIDERS.add(provider);
    }

    @Nullable
    public static HitProfile resolve(DamageSource source) {
        for (Function<DamageSource, HitProfile> provider : PROVIDERS) {
            HitProfile profile = provider.apply(source);
            if (profile != null) {
                return profile;
            }
        }
        return null;
    }

    /** The profile of the hit that is being applied on this thread, never null. */
    public static HitProfile current() {
        HitProfile profile = CURRENT.get();
        return profile == null ? HitProfile.NEUTRAL : profile;
    }

    public static <T> T with(@Nullable HitProfile profile, Supplier<T> action) {
        HitProfile previous = CURRENT.get();
        CURRENT.set(profile);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(@Nullable HitProfile profile, Runnable action) {
        with(profile, () -> {
            action.run();
            return null;
        });
    }
}
