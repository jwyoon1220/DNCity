package com.atsuishio.superbwarfare.entity.vehicle.utils

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity

/**
 * 外部飞行模型挂钩：附加模组(DNCity)可以为某些载具接管固定翼/直升机的受力与姿态计算。
 *
 * [VehicleEngineUtils.aircraftEngine] / [VehicleEngineUtils.helicopterEngine] 仍然负责输入、油门、襟翼、起落架、能量、音效与
 * 动画等状态；只有"速度如何变化、姿态如何转动"交给 [model]。[supports] 返回false的载具完全保持原有的SBW飞行逻辑。
 *
 * 约定：外部模型设置的是本tick结束时的真实速度(方块/tick)；SBW随后在baseTick里统一减去的重力会在调用处先补回。
 */
interface ExternalFlightModel {
    fun supports(vehicle: VehicleEntity): Boolean

    /** 固定翼：输入已由SBW整理好，模型负责速度与姿态(xRot/yRot/roll，沿用SBW渲染约定 Ry(-yRot)·Rx(xRot)·Rz(roll))。 */
    fun tickFixedWing(vehicle: VehicleEntity, controls: FixedWingInput)

    /** 直升机：姿态仍由SBW的周期变距逻辑控制，模型只负责旋翼推力、阻力、地面效应等带来的速度变化。collective为0..1。 */
    fun tickHelicopter(vehicle: VehicleEntity, collective: Float)

    /**
     * 固定翼原始输入，由外部模型自己实现"教官"(鼠标瞄准)逻辑。
     * aimX/aimY：鼠标准星相对屏幕中心的偏移(-1..1，aimX正=右，aimY正=下)；manualPitch：W/S手动俯仰(正=抬头，0=未按)；
     * manualRoll：A/D手动滚转(正=右压杆)；rudder：Q/E(正=右)；throttle 0..1，wep 0..1(超过100%油门的部分)；flaps/gear 0..1。
     */
    data class FixedWingInput(
        val aimX: Float,
        val aimY: Float,
        val manualPitch: Float,
        val manualRoll: Float,
        val rudder: Float,
        val throttle: Float,
        val wep: Float,
        val flaps: Float,
        val gear: Float,
        val airbrake: Boolean,
        val wheelBrake: Boolean,
    )

    companion object {
        @JvmStatic
        @Volatile
        var model: ExternalFlightModel? = null

        @JvmStatic
        fun of(vehicle: VehicleEntity): ExternalFlightModel? = model?.takeIf { it.supports(vehicle) }
    }
}
