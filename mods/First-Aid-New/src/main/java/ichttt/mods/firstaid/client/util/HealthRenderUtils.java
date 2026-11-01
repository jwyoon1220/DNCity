/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.client.util;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.health.InjuryEngine;
import ichttt.mods.firstaid.common.util.CommonUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Objects;

public final class HealthRenderUtils {
    public static final ResourceLocation SHOW_WOUNDS_LOCATION = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "textures/gui/show_wounds.png");
    public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");

    private static final Object2IntOpenHashMap<EnumPlayerPart> PREV_HEALTH = new Object2IntOpenHashMap<>();
    private static final EnumMap<EnumPlayerPart, FlashStateManager> FLASH_STATES = new EnumMap<>(EnumPlayerPart.class);

    static {
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            FLASH_STATES.put(part, new FlashStateManager());
        }
    }

    private HealthRenderUtils() {
    }

    public static void blit(GuiGraphics guiGraphics, ResourceLocation texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(texture, x, y, 0, (float) u, (float) v, width, height, textureWidth, textureHeight);
    }

    public static void blit(GuiGraphics guiGraphics, ResourceLocation texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height, int color) {
        float alpha = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        guiGraphics.setColor(red, green, blue, alpha);
        guiGraphics.blit(texture, x, y, 0, (float) u, (float) v, width, height, textureWidth, textureHeight);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static final int BLEED_LIGHT_COLOR = 0xFF6060;
    private static final int BLEED_HEAVY_COLOR = 0xFF1E1E;
    private static final int FRACTURE_COLOR = 0xFFD34D;
    private static final int TOURNIQUET_SAFE_COLOR = 0xFFB040;
    private static final int TOURNIQUET_ISCHEMIA_COLOR = 0xFF3030;
    private static final int DRESSED_COLOR = 0xD8D8D8;
    private static final int DESTROYED_COLOR = 0x808080;

    /**
     * Draws the injury markers of a part right aligned to {@code rightX}: B = light bleed, BB = heavy bleed, F = fracture.
     * Plain ASCII on purpose so they render with every font and resource pack.
     */
    public static void drawInjuryMarkers(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int rightX, int y) {
        int x = rightX;
        // X destroyed (needs surgery), T tourniquet with minutes left before ischemia, W dressed wound that can reopen
        if (damageablePart.isDestroyed()) {
            x -= font.width("X");
            guiGraphics.drawString(font, "X", x, y, DESTROYED_COLOR, false);
            x -= 3;
        }
        if (damageablePart.hasTourniquet()) {
            int safe = InjuryEngine.compressedTicks(InjuryEngine.TOURNIQUET_SAFE_REAL_SECONDS);
            int left = safe - damageablePart.tourniquetTicks;
            String marker = left > 0 ? "T" + Mth.ceil(left / 1200.0F) : "T!";
            x -= font.width(marker);
            guiGraphics.drawString(font, marker, x, y, left > 0 ? TOURNIQUET_SAFE_COLOR : TOURNIQUET_ISCHEMIA_COLOR, false);
            x -= 3;
        } else if (damageablePart.dressedWound != AbstractDamageablePart.BLEED_NONE) {
            x -= font.width("W");
            guiGraphics.drawString(font, "W", x, y, DRESSED_COLOR, false);
            x -= 3;
        }
        if (damageablePart.fractured) {
            x -= font.width("F");
            guiGraphics.drawString(font, "F", x, y, FRACTURE_COLOR, false);
            x -= 3;
        }
        if (damageablePart.bleedLevel != AbstractDamageablePart.BLEED_NONE) {
            boolean heavy = damageablePart.bleedLevel == AbstractDamageablePart.BLEED_HEAVY;
            String marker = heavy ? "BB" : "B";
            x -= font.width(marker);
            guiGraphics.drawString(font, marker, x, y, heavy ? BLEED_HEAVY_COLOR : BLEED_LIGHT_COLOR, false);
        }
    }

    public static void drawHealthString(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        int healthColor = getHealthColor(damageablePart);
        String text = TEXT_FORMAT.format(CommonUtils.getVisualHealth(damageablePart)) + "/" + damageablePart.getMaxHealth();
        guiGraphics.drawString(font, text, xTranslation, yTranslation, healthColor);
    }

    public static boolean healthChanged(AbstractDamageablePart damageablePart, boolean playerDead) {
        int current = (int) Math.ceil(damageablePart.currentHealth);
        FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
        if (PREV_HEALTH.containsKey(damageablePart.part)) {
            int prev = PREV_HEALTH.getInt(damageablePart.part);
            updatePrev(damageablePart.part, current, playerDead);
            if (prev != current) {
                activeFlashState.setActive(Util.getMillis());
                return true;
            }
            return false;
        }
        activeFlashState.setActive(Util.getMillis());
        updatePrev(damageablePart.part, current, playerDead);
        return true;
    }

    private static void updatePrev(EnumPlayerPart part, int current, boolean playerDead) {
        if (playerDead) {
            PREV_HEALTH.clear();
        } else {
            PREV_HEALTH.put(part, current);
        }
    }

    public static boolean drawAsString(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
        int totalHearts = getRenderedHeartSlots(damageablePart);
        return (allowSecondLine && totalHearts > 8) || (totalHearts > 12);
    }

    public static int getHeartRenderWidth(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
        int totalHearts = getRenderedHeartSlots(damageablePart);
        return getHeartsPerRow(totalHearts, allowSecondLine) * 9;
    }

    public static void drawHealth(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int current = (int) Math.ceil(CommonUtils.getVisualHealth(damageablePart));
        if (drawAsString(damageablePart, allowSecondLine)) {
            drawHealthString(guiGraphics, font, damageablePart, xTranslation, yTranslation, allowSecondLine);
            return;
        }

        FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
        boolean highlight = activeFlashState.update(Util.getMillis());
        boolean low = current < 1.25F;
        Minecraft minecraft = Minecraft.getInstance();
        int regen = -1;
        if (minecraft.player != null && FirstAidConfig.SERVER.allowOtherHealingItems.get() && minecraft.player.hasEffect(MobEffects.REGENERATION)) {
            regen = (minecraft.gui.getGuiTicks() / 2) % 15;
        }

        int currentHearts = getFilledHeartSlots(current);
        int totalHearts = Math.max(maxHealth, currentHearts);
        int heartsPerRow = getHeartsPerRow(totalHearts, allowSecondLine);
        int[] lowOffsets = new int[totalHearts];
        if (low) {
            for (int i = 0; i < lowOffsets.length; i++) {
                lowOffsets[i] = EventHandler.RAND.nextInt(2);
            }
        }

        Player player = minecraft.player;
        renderMax(regen, lowOffsets, 0, maxHealth, heartsPerRow, xTranslation, yTranslation, guiGraphics, player, highlight);
        if (totalHearts > maxHealth) {
            renderMax(regen, lowOffsets, maxHealth, totalHearts - maxHealth, heartsPerRow, xTranslation, yTranslation, guiGraphics, player, false);
        }
        renderCurrentHealth(regen, lowOffsets, current, heartsPerRow, xTranslation, yTranslation, guiGraphics, player, highlight);
    }

    private static int getRenderedHeartSlots(AbstractDamageablePart damageablePart) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int current = (int) Math.ceil(CommonUtils.getVisualHealth(damageablePart));
        return Math.max(maxHealth, getFilledHeartSlots(current));
    }

    private static int getFilledHeartSlots(int health) {
        return health / 2 + (health % 2 != 0 ? 1 : 0);
    }

    private static int getHeartsPerRow(int totalHearts, boolean allowSecondLine) {
        if (allowSecondLine && totalHearts > 4) {
            return 4;
        }
        return Math.max(totalHearts, 1);
    }

    public static int getMaxHearts(float value) {
        int maxCurrentHearts = Mth.ceil(value);
        if (maxCurrentHearts % 2 != 0) {
            maxCurrentHearts++;
        }
        return maxCurrentHearts >> 1;
    }

    private static void renderMax(int regen, int[] lowOffsets, int startSlot, int max, int heartsPerRow, int baseX, int baseY, GuiGraphics guiGraphics, Player player, boolean highlight) {
        renderHeartSprites(regen, lowOffsets, startSlot, max, false, heartsPerRow, baseX, baseY, guiGraphics, HeartSpriteHelper.container(player, highlight), HeartSpriteHelper.container(player, highlight));
    }

    private static void renderCurrentHealth(int regen, int[] lowOffsets, int current, int heartsPerRow, int baseX, int baseY, GuiGraphics guiGraphics, Player player, boolean highlight) {
        boolean renderLastHalf = current % 2 != 0;
        int render = getFilledHeartSlots(current);
        renderHeartSprites(regen, lowOffsets, 0, render, renderLastHalf, heartsPerRow, baseX, baseY, guiGraphics, HeartSpriteHelper.heart(player, false, true, highlight), HeartSpriteHelper.heart(player, false, false, highlight));
    }

    private static void renderHeartSprites(int regen, int[] lowOffsets, int startSlot, int toDraw, boolean lastOneHalf, int heartsPerRow, int baseX, int baseY, GuiGraphics guiGraphics, ResourceLocation halfSprite, ResourceLocation fullSprite) {
        if (toDraw <= 0) {
            return;
        }
        for (int i = 0; i < toDraw; i++) {
            boolean renderHalf = lastOneHalf && i + 1 == toDraw;
            int slot = startSlot + i;
            int x = baseX + 9 * (slot % heartsPerRow);
            int y = baseY + 5 * (slot / heartsPerRow) + (slot == regen ? -2 : 0) - lowOffsets[slot];
            guiGraphics.blitSprite(renderHalf ? halfSprite : fullSprite, x, y, 9, 9);
        }
    }

    public static int getHealthColor(AbstractDamageablePart damageablePart) {
        return getHealthColor(CommonUtils.getVisibleHealthRatio(damageablePart));
    }

    public static int getHealthColor(float healthRatio) {
        if (healthRatio > 0.85F) {
            return 0x63D56D;
        }
        if (healthRatio > 0.65F) {
            return 0xE2D142;
        }
        if (healthRatio > 0.40F) {
            return 0xE68F39;
        }
        return 0xD95145;
    }
}
