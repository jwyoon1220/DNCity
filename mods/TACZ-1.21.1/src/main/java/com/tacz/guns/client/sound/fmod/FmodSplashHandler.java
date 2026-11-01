package com.tacz.guns.client.sound.fmod;

import com.tacz.guns.GunMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Swaps in {@link FmodSplashScreen} the first time the title screen appears. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public final class FmodSplashHandler {
    private static boolean shown = false;

    private FmodSplashHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (shown) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen titleScreen) {
            shown = true;
            minecraft.setScreen(new FmodSplashScreen(titleScreen));
        }
    }
}
