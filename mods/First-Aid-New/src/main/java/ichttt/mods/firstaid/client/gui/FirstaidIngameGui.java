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

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a Tarkov-style per-body-part health readout (label + bar + current/max number)
 * in place of the vanilla heart row.
 */
public final class FirstaidIngameGui {
    private static final EnumPlayerPart[] DISPLAY_ORDER = {
            EnumPlayerPart.HEAD, EnumPlayerPart.BODY,
            EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM,
            EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG,
            EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT
    };
    private static final int ROWS = DISPLAY_ORDER.length + 1; // +1 for the aggregate "HP" row
    private static final int ROW_HEIGHT = 9;
    private static final int BAR_WIDTH = 90;
    private static final int BAR_HEIGHT = 5;

    private static final Map<EnumPlayerPart, String> LABELS = new EnumMap<>(EnumPlayerPart.class);
    private static int labelWidth = -1;

    private FirstaidIngameGui() {
    }

    public static void renderHealth(Gui gui, int width, int height, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        reserveHealthBarSpace(gui, player);

        AbstractPlayerDamageModel damageModel = CommonUtils.getOptionalDamageModel(player).orElse(null);
        if (damageModel == null) {
            return;
        }

        if (labelWidth < 0) {
            buildLabels(minecraft);
        }

        int left = width / 2 - 91;
        int top = height - gui.leftHeight + getReservedOffset(player);
        int barX = left + labelWidth + 4;

        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float overallMax = Math.max((float) attrMaxHealth.getValue(), player.getHealth());
        float overallCurrent = Mth.clamp(getModelDisplayHealth(player, damageModel), 0.0F, overallMax);
        float overallRatio = overallMax <= 0.0F ? 0.0F : overallCurrent / overallMax;
        int overallColor = HealthRenderUtils.getHealthColor(overallRatio);

        int y = top;
        guiGraphics.drawString(minecraft.font, "HP", left, y + 1, 0xFFFFFF, false);
        drawBar(guiGraphics, barX, y, overallRatio, overallColor);
        String overallText = HealthRenderUtils.TEXT_FORMAT.format(overallCurrent) + "/" + Mth.ceil(overallMax);
        guiGraphics.drawString(minecraft.font, overallText, barX + BAR_WIDTH + 4, y + 1, overallColor, false);
        y += ROW_HEIGHT;

        for (EnumPlayerPart part : DISPLAY_ORDER) {
            AbstractDamageablePart damageablePart = damageModel.getFromEnum(part);
            guiGraphics.drawString(minecraft.font, LABELS.get(part), left, y + 1, 0xFFFFFF, false);
            HealthRenderUtils.drawInjuryMarkers(guiGraphics, minecraft.font, damageablePart, left - 3, y + 1);
            float ratio = CommonUtils.getVisibleHealthRatio(damageablePart);
            drawBar(guiGraphics, barX, y, ratio, HealthRenderUtils.getHealthColor(damageablePart));
            HealthRenderUtils.drawHealthString(guiGraphics, minecraft.font, damageablePart, barX + BAR_WIDTH + 4, y + 1, false);
            y += ROW_HEIGHT;
        }
    }

    private static void drawBar(GuiGraphics guiGraphics, int x, int y, float ratio, int color) {
        int fillWidth = Math.round(BAR_WIDTH * Mth.clamp(ratio, 0.0F, 1.0F));
        guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xAA000000);
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, 0xFF000000 | color);
        }
    }

    private static synchronized void buildLabels(Minecraft minecraft) {
        labelWidth = 0;
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            String translated = I18n.get("firstaid.gui." + part.toString().toLowerCase(Locale.ENGLISH));
            labelWidth = Math.max(labelWidth, minecraft.font.width(translated));
            LABELS.put(part, translated);
        }
    }

    public static void reserveHealthBarSpace(Gui gui, Player player) {
        gui.leftHeight += ROWS * ROW_HEIGHT;
    }

    private static int getReservedOffset(Player player) {
        return ROWS * ROW_HEIGHT;
    }

    private static float getModelDisplayHealth(Player player, AbstractPlayerDamageModel damageModel) {
        if (!(damageModel instanceof PlayerDamageModel model)) {
            return player.getHealth();
        }

        float displayHealth = model.projectVanillaHealth(player);
        return displayHealth <= 0.0F && player.isAlive() && !damageModel.isDead(player) ? 1.0F : displayHealth;
    }

}

