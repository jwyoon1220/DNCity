package com.atsuishio.superbwarfare.mixins;

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.init.ModMobEffects;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    // 按键修改mixin
    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(boolean pIsSneaking, float pSneakingSpeedMultiplier, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        var tag = NBTTool.getTag(stack);

        if (stack.is(ModItems.MONITOR.get()) && tag.getBoolean("Using") && tag.getBoolean("Linked")) {
            this.up = false;
            this.down = false;
            this.left = false;
            this.right = false;
            this.shiftKeyDown = false;
            this.forwardImpulse = 0;
            this.leftImpulse = 0;
            this.jumping = false;
        }

        // 驾驶固定翼飞机(EngineInfo.Aircraft)时，Shift/Ctrl/WASD已经被这个模组自己的
        // ModKeyMappings读作油门/俯仰输入；驾驶直升机(EngineInfo.Helicopter)时W/S同样已经是
        // 集合距(collective)输入——两者现在都会释放鼠标进入Mouse Aim模式，必须彻底抑制原版对应行为
        // （潜行动画/下船判定、疾跑FOV/粒子、残留的移动冲量），否则两套输入会互相打架。
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof VehicleEntity superbwarfareVehicle
                && superbwarfareVehicle.getFirstPassenger() == player
                && (superbwarfareVehicle.computed().getEngineType() == EngineType.AIRCRAFT
                || superbwarfareVehicle.computed().getEngineType() == EngineType.HELICOPTER)) {
            this.up = false;
            this.down = false;
            this.left = false;
            this.right = false;
            this.shiftKeyDown = false;
            this.forwardImpulse = 0;
            this.leftImpulse = 0;
            this.jumping = false;
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }

        if (Minecraft.getInstance().player == null
                || !Minecraft.getInstance().player.hasEffect(ModMobEffects.SHOCK)
                || Minecraft.getInstance().player.isSpectator()
        ) return;

        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.shiftKeyDown = false;
        this.forwardImpulse = 0;
        this.leftImpulse = 0;
        this.jumping = false;
    }
}
