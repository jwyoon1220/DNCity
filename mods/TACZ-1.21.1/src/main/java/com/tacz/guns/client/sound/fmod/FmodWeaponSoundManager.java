package com.tacz.guns.client.sound.fmod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.joml.Vector3f;

import com.iwei20.fmod.FModLoad;
import com.iwei20.fmod.studio.FMODException;
import com.iwei20.fmod.studio.FMODSystem;
import com.iwei20.fmod.studio.FMODSystem.Attributes3D;
import com.tacz.guns.GunMod;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Owns the client-side FMOD Studio system used to play weapon sounds from DNCity's
 * weapons.bank (see {@link com.tacz.guns.WeaponsEvents}) in place of TACZ's normal (OGG,
 * vanilla sound engine) gun sounds. See {@link WeaponFmodEvents} for the gun/action -> event
 * mapping.
 *
 * <p>Initialization is best-effort: if native library loading, bank loading, or FMOD system
 * creation fails for any reason (missing natives for the current OS/arch, DNCity not loaded,
 * a corrupt bank, etc.), this logs a warning once and every subsequent play call becomes a
 * no-op, rather than crashing the game.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public final class FmodWeaponSoundManager {
    private static final String BANK_SOURCE_MOD_ID = "dncity";
    private static final String MASTER_BANK_RESOURCE = "fmod/Master.bank";
    private static final String MASTER_STRINGS_BANK_RESOURCE = "fmod/Master.strings.bank";
    // Must match the real file's exact case (src/main/resources/fmod/Weapons.bank in the dncity
    // project) -- FmodBankLocator resolves this through IModFile.findResource against a real jar
    // entry in a packaged build, which is case-sensitive, unlike a dev-run's loose classpath
    // directory lookup (case-insensitive on Windows). A lowercase "weapons.bank" here loaded fine
    // under runClient but silently failed (IOException, caught, falls back to no weapon sound at
    // all) once actually packaged and installed -- confirmed from a real installed instance's
    // crash log pointing at FmodBankLocator.extractBank's "resource not found" throw.
    private static final String WEAPONS_BANK_RESOURCE = "fmod/Weapons.bank";

    private static boolean initAttempted = false;
    private static FMODSystem system = null;

    /**
     * The most recently started "other" (non-shoot) event instance, e.g. from
     * {@link #playReloadTactical}, {@link #playBolt}, {@link #playDraw}, or
     * {@link #playPutAway}. Mirrors {@code SoundPlayManager.tmpSoundInstance}: callers use
     * {@link #stopLast} to cut it off early when a new action (draw/reload/inspect/etc.)
     * interrupts it.
     */
    private static volatile FMODSystem.EventInstance lastOtherInstance = null;

    private FmodWeaponSoundManager() {
    }

    private static synchronized void ensureInitialized() {
        if (initAttempted) {
            return;
        }
        initAttempted = true;

        try {
            FModLoad.load();

            FMODSystem newSystem = new FMODSystem();
            // Master.bank references at least one DSP/plugin type this build's FMOD doesn't
            // ship (e.g. a third-party effect on the master bus), which otherwise fails the
            // *entire* bank load with FMOD_ERR_OUTPUT_INIT ("output, dsp unit type or codec
            // not available"). AllowMissingPlugins loads the bank anyway; only that specific
            // effect is silently skipped.
            newSystem.initialize(FMODSystem.InitFlags.AllowMissingPlugins.getConstant());

            Path bankDir = Files.createTempDirectory("dncity-fmod-banks-");
            bankDir.toFile().deleteOnExit();

            Path masterBankFile = FmodBankLocator.extractBank(BANK_SOURCE_MOD_ID, MASTER_BANK_RESOURCE, bankDir);
            masterBankFile.toFile().deleteOnExit();
            newSystem.loadBankFile(masterBankFile.toString());

            // Needed to resolve events by path (event:/Weapons/...) rather than GUID -- see
            // WeaponFmodEvents.
            Path stringsBankFile = FmodBankLocator.extractBank(BANK_SOURCE_MOD_ID, MASTER_STRINGS_BANK_RESOURCE, bankDir);
            stringsBankFile.toFile().deleteOnExit();
            newSystem.loadBankFile(stringsBankFile.toString());

            Path weaponsBankFile = FmodBankLocator.extractBank(BANK_SOURCE_MOD_ID, WEAPONS_BANK_RESOURCE, bankDir);
            weaponsBankFile.toFile().deleteOnExit();
            FMODSystem.Bank weaponsBank = newSystem.loadBankFile(weaponsBankFile.toString());
            weaponsBank.loadSampleData();

            system = newSystem;
            GunMod.LOGGER.info("[TACZ FMOD] Weapon sound system initialized ({}, {}, {})", MASTER_BANK_RESOURCE, MASTER_STRINGS_BANK_RESOURCE, WEAPONS_BANK_RESOURCE);
        } catch (IOException | FMODException | UnsupportedOperationException | UnsatisfiedLinkError e) {
            GunMod.LOGGER.warn("[TACZ FMOD] Failed to initialize weapon sound system; falling back to vanilla gun sounds.", e);
            system = null;
        }
    }

    /**
     * Plays a one-shot FMOD weapons.bank event at a fixed world position.
     *
     * @param eventGuid One of the {@code WEAPONS_*} constants from
     *     {@link com.tacz.guns.WeaponsEvents}.
     * @param position The world position to play the event at.
     */
    public static void playAt(String eventGuid, Vec3 position) {
        ensureInitialized();
        if (system == null) {
            return;
        }
        try {
            FMODSystem.EventInstance instance = system.getEvent(eventGuid).createInstance();
            instance.set3DAttributes(Attributes3D.stationary((float) position.x, (float) position.y, (float) position.z));
            instance.start();
            instance.release();
        } catch (FMODException e) {
            GunMod.LOGGER.warn("[TACZ FMOD] Failed to play event {}", eventGuid, e);
        }
    }

    /**
     * Plays a one-shot FMOD weapons.bank event at an entity's current position.
     *
     * @param eventGuid One of the {@code WEAPONS_*} constants from
     *     {@link com.tacz.guns.WeaponsEvents}.
     * @param entity The entity whose position the event should play at.
     */
    public static void playAt(String eventGuid, Entity entity) {
        playAt(eventGuid, entity.position());
    }

    /**
     * Plays a gun's shoot event, chosen via {@link WeaponFmodEvents#shootEventFor}, with the
     * {@code IsOutside} parameter set from whether the shooter can currently see the sky (a
     * cheap, robust indoor/outdoor proxy) -- these events' gunshot acoustics (reverb send,
     * tail/echo layers) are authored in the bank to differ noticeably by that parameter. Silently
     * skipped for whichever fallback/default events don't define it (checked via {@link
     * FMODSystem.EventDescription#getParameterNames}, not just try/caught, so a genuine FMOD
     * failure to set it on an event that *does* have it isn't swallowed the same way).
     */
    public static void playShoot(Entity entity, ResourceLocation gunDisplayId) {
        ensureInitialized();
        if (system == null) {
            return;
        }
        try {
            FMODSystem.EventDescription description = system.getEvent(WeaponFmodEvents.shootEventFor(gunDisplayId));
            FMODSystem.EventInstance instance = description.createInstance();
            Vec3 position = entity.position();
            instance.set3DAttributes(Attributes3D.stationary((float) position.x, (float) position.y, (float) position.z));
            if (description.getParameterNames().contains("IsOutside")) {
                boolean outside = entity.level().canSeeSky(entity.blockPosition());
                instance.setParameterByName("IsOutside", outside ? 1f : 0f);
            }
            instance.start();
            instance.release();
        } catch (FMODException e) {
            GunMod.LOGGER.warn("[TACZ FMOD] Failed to play shoot event for {}", gunDisplayId, e);
        }
    }

    public static void playReloadTactical(Entity entity, ResourceLocation gunDisplayId) {
        playOther(WeaponFmodEvents.reloadTacticalEventFor(gunDisplayId), entity);
    }

    public static void playReloadEmpty(Entity entity, ResourceLocation gunDisplayId) {
        playOther(WeaponFmodEvents.reloadEmptyEventFor(gunDisplayId), entity);
    }

    public static void playBolt(Entity entity) {
        playOther(WeaponFmodEvents.BOLT, entity);
    }

    public static void playDraw(Entity entity, ResourceLocation gunDisplayId) {
        playOther(WeaponFmodEvents.drawEventFor(gunDisplayId), entity);
    }

    public static void playPutAway(Entity entity, ResourceLocation gunDisplayId) {
        playOther(WeaponFmodEvents.putAwayEventFor(gunDisplayId), entity);
    }

    /**
     * Stops whichever "other" event ({@link #playReloadTactical}, {@link #playBolt},
     * {@link #playDraw}, or {@link #playPutAway}) most recently started, if it's still
     * playing. Mirrors {@code SoundPlayManager#stopPlayGunSound()}, called right before those
     * same actions start a new sound so a cancelled/interrupted action doesn't leave its
     * sound playing.
     */
    public static void stopLast() {
        FMODSystem.EventInstance instance = lastOtherInstance;
        lastOtherInstance = null;
        if (instance == null) {
            return;
        }
        try {
            instance.stop();
        } catch (FMODException e) {
            // Benign race: the instance already finished playing on its own (these are all
            // short one-shot foley sounds) and was cleaned up by release() before this call
            // caught up to it. Nothing to stop.
        }
    }

    private static void playOther(String eventGuid, Entity entity) {
        ensureInitialized();
        if (system == null) {
            return;
        }
        try {
            FMODSystem.EventInstance instance = system.getEvent(eventGuid).createInstance();
            Vec3 position = entity.position();
            instance.set3DAttributes(Attributes3D.stationary((float) position.x, (float) position.y, (float) position.z));
            instance.start();
            lastOtherInstance = instance;
            instance.release();
        } catch (FMODException e) {
            GunMod.LOGGER.warn("[TACZ FMOD] Failed to play event {}", eventGuid, e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (system == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            Vec3 pos = camera.getPosition();
            Vector3f look = camera.getLookVector();
            Vector3f up = camera.getUpVector();
            system.setListenerAttributes(0, new Attributes3D(
                    new FMODSystem.Vector3((float) pos.x, (float) pos.y, (float) pos.z),
                    FMODSystem.Vector3.ZERO,
                    new FMODSystem.Vector3(look.x(), look.y(), look.z()),
                    new FMODSystem.Vector3(up.x(), up.y(), up.z())));
        }

        try {
            system.update();
        } catch (FMODException e) {
            GunMod.LOGGER.warn("[TACZ FMOD] System update failed", e);
        }
    }
}
