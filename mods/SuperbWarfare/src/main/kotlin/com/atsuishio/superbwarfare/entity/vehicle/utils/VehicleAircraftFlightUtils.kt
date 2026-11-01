package com.atsuishio.superbwarfare.entity.vehicle.utils

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineInfo
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player

/**
 * War Thunder风格"Mouse Aim"核心飞行模型的辅助逻辑：油门/WEP、襟翼、减速板、起落架气动效果。
 *
 * 只处理这些系统本身的数值/状态更新；实际如何把它们喂入偏航/俯仰/滚转仍然在
 * [VehicleEngineUtils.aircraftEngine] 里完成，本文件不重复已有的气动模型（转弯半径、
 * 自动回正等），只做"新增系统"的部分，供所有固定翼载具(A10/Ju87/AC130H/AirSheep/Drone等)共用。
 */
object VehicleAircraftFlightUtils {

    /** 极高速度下强行收起落架容易损坏起落架，超过此水平速度只警告不收回 */
    private const val MAX_SAFE_GEAR_RETRACT_SPEED = 1.2

    /** 油门轴的WEP上限（100%以上部分即为战时应急功率区间） */
    const val THROTTLE_WEP_CAP = 1.3f

    /**
     * 更新油门(0f~1f为正常范围，1f~[THROTTLE_WEP_CAP]为WEP区间)与WEP过热值。
     *
     * 按照War Thunder的参照方案，WEP不是一个独立的开关键，而是同一根油门轴的延伸：
     * Shift持续按住把油门推起来，推过100%之后继续按住Shift就是在用WEP；Ctrl持续按住把油门收下来。
     * 过热只在真正超过100%并停留在那里时才累积——回到100%以内立刻开始冷却；
     * 打满热量后油门被强制顶回100%上限，直到冷却到位才能再次推过界，这就是WEP"有代价"的部分。
     */
    @JvmStatic
    fun VehicleEntity.updateAircraftThrottle(engineInfo: EngineInfo.Aircraft) {
        // 约2秒从0推到100%(原来约4秒，太迟钝)
        val throttleStep = 0.025f

        if (downInputDown && !sprintInputDown) {
            throttle = Mth.clamp(throttle + throttleStep, 0f, THROTTLE_WEP_CAP)
        } else if (sprintInputDown && !downInputDown) {
            throttle = Mth.clamp(throttle - throttleStep, 0f, THROTTLE_WEP_CAP)
        }

        val overBoosting = throttle > 1f
        wepHeat = if (overBoosting) {
            Mth.clamp(wepHeat + 0.012f, 0f, 1f)
        } else {
            Mth.clamp(wepHeat - 0.006f, 0f, 1f)
        }

        // 过热：强制把油门顶回100%上限，直到冷却到位
        if (wepHeat >= 1f && throttle > 1f) {
            throttle = 1f
        }
    }

    /**
     * 检测襟翼开关键的上升沿并切换[VehicleEntity.flapsDown]。
     * 真正的空气动力学效果（转弯半径变小/最高速下降）在aircraftEngine里按flapsDown取值应用。
     */
    @JvmStatic
    fun VehicleEntity.updateFlapsToggle() {
        if (flapsToggleInputDown && !wasFlapsToggleInputDown) {
            flapsDown = !flapsDown
        }
        wasFlapsToggleInputDown = flapsToggleInputDown
    }

    /**
     * 起落架收放的高速保护：高速强行收起落架容易造成结构损坏，这里简单处理为
     * "速度过高就不收，只提示玩家"，而不是复杂的起落架损坏建模。
     *
     * @return 是否允许这一次收起落架的请求
     */
    @JvmStatic
    fun VehicleEntity.canRetractGearSafely(): Boolean {
        val speed = deltaMovement.horizontalDistance()
        if (speed <= MAX_SAFE_GEAR_RETRACT_SPEED) return true

        if (!level().isClientSide) {
            val passenger = getFirstPassenger()
            if (passenger is Player) {
                passenger.displayClientMessage(
                    Component.translatable("tips.superbwarfare.gear_retract_too_fast"), true
                )
            }
        }
        return false
    }
}
