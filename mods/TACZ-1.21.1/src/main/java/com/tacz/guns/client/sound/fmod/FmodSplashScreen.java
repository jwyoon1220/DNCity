package com.tacz.guns.client.sound.fmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * "Made with FMOD" splash shown once at startup, ahead of the title screen -- FMOD's indie
 * license requires the logo be displayed on startup.
 *
 * <p>The logo texture ({@code dncity:textures/gui/fmod_logo.png}) ships with DNCity, not
 * TACZ, but Minecraft's resource manager merges every mod's assets into one namespace, so
 * referencing another mod's texture by {@link ResourceLocation} works fine.
 *
 * <p>Dismisses to {@code nextScreen} after {@link #DURATION_MS}, or immediately on any
 * click/key press.
 */
public class FmodSplashScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath("dncity", "textures/gui/fmod_logo.png");
    private static final int LOGO_WIDTH = 728;
    private static final int LOGO_HEIGHT = 192;
    private static final long DURATION_MS = 3000L;

    private final Screen nextScreen;
    private long openedAt;

    public FmodSplashScreen(Screen nextScreen) {
        super(Component.literal("FMOD"));
        this.nextScreen = nextScreen;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xFF000000);

        float scale = Math.min(width * 0.35f / LOGO_WIDTH, 0.5f);
        int drawWidth = (int) (LOGO_WIDTH * scale);
        int drawHeight = (int) (LOGO_HEIGHT * scale);
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;
        guiGraphics.blit(LOGO, x, y, drawWidth, drawHeight, 0f, 0f, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);

        if (System.currentTimeMillis() - openedAt >= DURATION_MS && minecraft != null) {
            minecraft.setScreen(nextScreen);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft != null) {
            minecraft.setScreen(nextScreen);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null) {
            minecraft.setScreen(nextScreen);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
