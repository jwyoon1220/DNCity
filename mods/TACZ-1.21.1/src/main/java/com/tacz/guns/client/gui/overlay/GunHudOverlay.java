package com.tacz.guns.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;


public class GunHudOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation SEMI = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation AUTO = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation BURST = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/fire_mode_burst.png");
    private static final ResourceLocation HEATBAR = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/heat_bar.png");
    private static final ResourceLocation HEATBASE = ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/heat_base.png");

    private static long checkAmmoTimestamp = -1L;
    private static int cacheMaxAmmoCount = 0;
    private static int cacheInventoryAmmoCount = 0;

    private static final int MAX_AMMO_COUNT = 9999;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(stack);

        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null) {
            return;
        }

        // 是否背包直读
        boolean useInventoryAmmo = iGun.useInventoryAmmo(stack);
        // 是否完全过热
        boolean overheatLocked = gunData.hasHeatData() && iGun.isOverheatLocked(stack);
        // DNCity: 不显示弹药数 (弹匣/备弹)，只保留缓存计算 (背包直读枪依赖它)
        handleCacheCount(player, stack, gunData, iGun, useInventoryAmmo);

        // 竖线
        graphics.fill(width - 75, height - 43, width - 74, height - 25, 0xFFFFFFFF);

        PoseStack poseStack = graphics.pose();

        Font font = mc.font;

        // 模组版本信息
        String minecraftVersion = SharedConstants.getCurrentVersion().getName();
        String modVersion = ModList.get().getModFileById(GunMod.MOD_ID).versionString();
        String debugInfo = String.format("%s-%s", minecraftVersion, modVersion);
        // 文本
        poseStack.pushPose();
        poseStack.scale(0.5f, 0.5f, 1);
        graphics.drawString(font, debugInfo, (int) ((width - 70) / 0.5f), (int) ((height - 29f) / 0.5f), 0xffaaaaaa);
        poseStack.popPose();

        // 图标渲染
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 获取图标
        ResourceLocation hudTexture = display.getHUDTexture();
        @Nullable ResourceLocation hudEmptyTexture = display.getHudEmptyTexture();

        // DNCity: 空枪不再变红 (不透露弹药数)，只有过热时变红
        if (overheatLocked) {
            if (hudEmptyTexture == null) {
                RenderSystem.setShaderColor(1, 0.3f, 0.3f, 1);
            } else {
                hudTexture = hudEmptyTexture;
            }
        }
        // 渲染枪械图标
        graphics.blit(hudTexture, width - 117, height - 44, 0, 0, 39, 13, 39, 13);

        // 渲染开火模式图标
        FireMode fireMode = IGun.getMainHandFireMode(player);
        ResourceLocation fireModeTexture = switch (fireMode) {
            case AUTO -> AUTO;
            case BURST -> BURST;
            default -> SEMI;
        };
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(fireModeTexture, width - 70, height - 43, 0, 0, 10, 10, 10, 10);
    }

    private static void handleCacheCount(LocalPlayer player, ItemStack stack, GunData gunData, IGun iGun, boolean useInventoryAmmo) {
        // 0.05 秒 (1 tick) 检查一次
        if ((System.currentTimeMillis() - checkAmmoTimestamp) > 50) {
            checkAmmoTimestamp = System.currentTimeMillis();
            // 当前枪械的总弹药数
            cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
            // 玩家背包弹药数
            if (IGunOperator.fromLivingEntity(player).needCheckAmmo()) {
                if (iGun.useDummyAmmo(stack)) {
                    // 缓存虚拟弹药数
                    cacheInventoryAmmoCount = iGun.getDummyAmmoAmount(stack);
                } else {
                    // 缓存背包内的弹药数
                    handleInventoryAmmo(stack, player.getInventory());
                }
            } else {
                cacheInventoryAmmoCount = MAX_AMMO_COUNT;
            }
            if (useInventoryAmmo) {
                iGun.setCurrentAmmoCount(stack, cacheInventoryAmmoCount);
            }
        }
    }

    private static void handleInventoryAmmo(ItemStack stack, Inventory inventory) {
        cacheInventoryAmmoCount = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack inventoryItem = inventory.getItem(i);
            if (inventoryItem.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(stack, inventoryItem)) {
                cacheInventoryAmmoCount += inventoryItem.getCount();
            }
            if (inventoryItem.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(stack, inventoryItem)) {
                // 创造模式弹药箱？直接返回 9999
                if (iAmmoBox.isAllTypeCreative(inventoryItem) || iAmmoBox.isCreative(inventoryItem)) {
                    cacheInventoryAmmoCount = 9999;
                    return;
                }
                cacheInventoryAmmoCount += iAmmoBox.getAmmoCount(inventoryItem);
            }
        }
    }
}
