package com.atsuishio.superbwarfare.entity.vehicle.utils

import com.atsuishio.superbwarfare.data.gun.GunData
import com.atsuishio.superbwarfare.data.gun.GunProp
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils.getXRotFromVector
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils.getYRotFromVector
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils.transformPosition
import com.atsuishio.superbwarfare.event.ClientEventHandler
import com.atsuishio.superbwarfare.event.ClientMouseHandler
import com.atsuishio.superbwarfare.tools.maxZoom
import com.mojang.math.Axis
import net.minecraft.util.Mth
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix4d

/**
 * 处理载具客户端专用方法的工具类
 */
object VehicleClientUtils {

    /**
     * 获取视角旋转
     *
     * @param vehicle       载具
     * @param partialTicks  部分刻
     * @param player        玩家
     * @param zoom          是否在载具上瞄准
     * @param isFirstPerson 是否是第一人称视角
     * @return 视角旋转角度，如果不需要自定义视角则返回null
     */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun getCameraRotation(
        vehicle: VehicleEntity,
        partialTicks: Float,
        player: Player,
        zoom: Boolean,
        isFirstPerson: Boolean
    ): Vec2? {
        val index = vehicle.getSeatIndex(player)
        val seat = vehicle.computed().seats().getOrNull(index)
        val gunData = vehicle.getGunData(player)
        if (seat != null) {
            val data = seat.cameraPos
            if (data != null) {
                if (zoom && gunData != null && gunData.get(GunProp.SHOOT_POS).viewDirection != null) {
                    return if (ClientEventHandler.isNacelleCam(player)) {
                        Vec2(
                            (-getYRotFromVector(vehicle.getViewVec(player, partialTicks)).toFloat() - ClientMouseHandler.nacelleCameraYaw).toFloat(),
                            (-getXRotFromVector(vehicle.getViewVec(player, partialTicks)).toFloat() + ClientMouseHandler.nacelleCameraPitch).toFloat()
                        )
                    } else Vec2(
                        -getYRotFromVector(vehicle.getViewVec(player, partialTicks)).toFloat(),
                        -getXRotFromVector(vehicle.getViewVec(player, partialTicks)).toFloat()
                    )
                }
                if (vehicle.useAircraftCamera(index)) {
                    return if (ClientEventHandler.isNacelleCam(player)) {
                        Vec2(
                            (vehicle.getYaw(partialTicks) - ClientMouseHandler.nacelleCameraYaw).toFloat(),
                            (vehicle.getPitch(partialTicks) + ClientMouseHandler.nacelleCameraPitch).toFloat()
                        )
                    } else {
                        // 驾驶员第三人称：镜头只跟随航向，不跟随机体俯仰
                        val vehiclePitch = if (pitchFreeCamera(vehicle, player, isFirstPerson)) 0f else vehicle.getPitch(partialTicks)
                        Vec2(
                            (vehicle.getYaw(partialTicks) - ClientMouseHandler.freeCameraYaw).toFloat(),
                            (vehiclePitch + ClientMouseHandler.freeCameraPitch).toFloat()
                        )
                    }
                }
                if (zoom || isFirstPerson) {
                    return Vec2(
                        -getYRotFromVector(vehicle.cameraDirection(player, partialTicks)).toFloat(),
                        -getXRotFromVector(vehicle.cameraDirection(player, partialTicks)).toFloat()
                    )
                }
            } else {
                return null
            }
        }
        return null
    }

    /**
     * 固定翼/直升机驾驶员的第三人称视角不随机体俯仰转动(机头上下时镜头保持水平，只跟随航向)，
     * 第一人称座舱视角和其他座位保持原样。
     */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun pitchFreeCamera(vehicle: VehicleEntity, player: Player, isFirstPerson: Boolean): Boolean =
        !isFirstPerson && vehicle.firstPassenger == player
                && (vehicle.vehicleType == VehicleType.AIRPLANE || vehicle.vehicleType == VehicleType.HELICOPTER)

    /** 与[VehicleVecUtils.getClientVehicleTransform]相同，但去掉机体俯仰，只保留航向和自由视角偏移 */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun pitchFreeCameraTransform(vehicle: VehicleEntity, partialTicks: Float): Matrix4d {
        val transform = Matrix4d()
        transform.translate(
            Mth.lerp(partialTicks.toDouble(), vehicle.xo, vehicle.x),
            Mth.lerp(partialTicks.toDouble(), vehicle.yo + vehicle.rotateOffsetHeight, vehicle.y + vehicle.rotateOffsetHeight),
            Mth.lerp(partialTicks.toDouble(), vehicle.zo, vehicle.z)
        )
        transform.rotate(Axis.YP.rotationDegrees((-Mth.lerp(partialTicks, vehicle.yRotO, vehicle.yRot) + ClientMouseHandler.freeCameraYaw).toFloat()))
        transform.rotate(Axis.XP.rotationDegrees(ClientMouseHandler.freeCameraPitch.toFloat()))
        return transform
    }

    /**
     * 获取视角位置
     *
     * @param vehicle       载具
     * @param partialTicks  部分刻
     * @param player        玩家
     * @param zoom          是否在载具上瞄准
     * @param isFirstPerson 是否是第一人称视角
     * @return 视角位置，如果不需要自定义视角则返回null
     */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun getCameraPosition(
        vehicle: VehicleEntity,
        partialTicks: Float,
        player: Player,
        zoom: Boolean,
        isFirstPerson: Boolean
    ): Vec3? {
        val index = vehicle.getSeatIndex(player)
        val seat = vehicle.computed().seats().getOrNull(index)
        if (seat != null) {
            val data = seat.cameraPos
            val gunData = vehicle.getGunData(player)
            if (data != null) {
                if (zoom || isFirstPerson) {
                    return if (zoom) {
                        if (gunData != null && gunData.get(GunProp.SHOOT_POS).viewPosition != null) {
                            vehicle.getViewPos(player, partialTicks)
                        } else {
                            vehicle.getZoomPos(player, partialTicks)
                        }
                    } else {
                        vehicle.getCameraPos(player, partialTicks)
                    }
                } else if (vehicle.useAircraftCamera(index)) {
                    val transform = if (pitchFreeCamera(vehicle, player, isFirstPerson)) {
                        pitchFreeCameraTransform(vehicle, partialTicks)
                    } else {
                        vehicle.getClientVehicleTransform(partialTicks)
                    }
                    val maxCameraPosition = transformPosition(
                        transform,
                        data.aircraftCameraPos.x,
                        data.aircraftCameraPos.y + 0.1 * ClientMouseHandler.custom3pDistanceLerp,
                        data.aircraftCameraPos.z - ClientMouseHandler.custom3pDistanceLerp
                    )
                    return maxCameraPosition.maxZoom(transform)
                }
            }
            return null
        }
        return null
    }

    /**
     * 是否使用载具固定视角
     *
     * @param vehicle 载具
     * @param entity  乘客实体
     * @return 是否使用固定视角
     */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun useFixedCameraPos(vehicle: VehicleEntity, entity: Entity?): Boolean {
        val index = vehicle.getSeatIndex(entity)
        val seat = vehicle.computed().seats().getOrNull(index) ?: return false
        val data = seat.cameraPos ?: return false
        return data.useFixedCameraPos
    }

    /**
     * 客户端第一人称弹药HUD组件
     */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun firstPersonAmmoComponent(vehicle: VehicleEntity, data: GunData, player: Player?): Component {
        val name = data.get(GunProp.NAME)
        if (name.isNullOrBlank()) return Component.empty()

        val ammoCount = vehicle.getAmmoCount(player)
        return Component.translatable(name, if (ammoCount == Int.MAX_VALUE) "∞" else ammoCount)
    }

    /**
     * 客户端第三人称弹药HUD组件
     */
    @OnlyIn(Dist.CLIENT)
    @JvmStatic
    fun thirdPersonAmmoComponent(vehicle: VehicleEntity, data: GunData, player: Player?): Component {
        return firstPersonAmmoComponent(vehicle, data, player)
    }
}
