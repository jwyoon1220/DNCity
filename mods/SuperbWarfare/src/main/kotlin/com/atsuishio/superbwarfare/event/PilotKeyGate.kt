package com.atsuishio.superbwarfare.event

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity
import com.atsuishio.superbwarfare.init.ModKeyMappings
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * War Thunder式驾驶键位：驾驶固定翼/直升机时，W/S俯仰(直升机为周期变距)、A/D滚转、Q/E偏航、Shift/Ctrl油门(总距)。
 *
 * 这些键和原版/SBW的交互键冲突(E背包、Q丢弃、F副手、U卸载乘客、Y断开牵引)，所以驾驶时交互键必须按住Ctrl才生效；
 * 下机键(DISMOUNT)不受影响。按住Ctrl配合交互键时，这一下不算作油门减少/偏航/襟翼输入。
 */
@EventBusSubscriber(Dist.CLIENT)
object PilotKeyGate {
    private val mc: Minecraft get() = Minecraft.getInstance()

    /** 本地玩家正在驾驶(第一乘客)固定翼或直升机。 */
    @JvmStatic
    fun piloting(): Boolean {
        val player = mc.player ?: return false
        val vehicle = player.vehicle as? VehicleEntity ?: return false
        if (vehicle.firstPassenger != player) return false
        val type = vehicle.computed().engineType
        return type == EngineType.AIRCRAFT || type == EngineType.HELICOPTER
    }

    private fun ctrl(): Boolean = ModKeyMappings.MOVE_CTRL.isDown

    /** 原版交互键：驾驶时与飞行键冲突的那些。 */
    private fun gatedVanillaKeys(): List<KeyMapping> = mc.options.let { listOf(it.keyInventory, it.keyDrop, it.keySwapOffhand) }

    private fun gatedModKeys(): List<KeyMapping> = listOf(ModKeyMappings.UNLOAD_PASSENGERS, ModKeyMappings.DISCONNECT_TOWING)

    /** 交互键此刻是否可用：不在驾驶，或按住了Ctrl。 */
    @JvmStatic
    fun interactionAllowed(): Boolean = !piloting() || ctrl()

    /** 正按着Ctrl+交互键：这一下的Ctrl/Q/E/F不应作为飞行输入。 */
    @JvmStatic
    fun interactionChord(): Boolean =
        piloting() && ctrl() && (gatedVanillaKeys() + gatedModKeys()).any { it.isDown }

    /** 原版在tick里通过consumeClick处理这些键，这里在它之前把驾驶时没按Ctrl的点击吃掉。 */
    @SubscribeEvent
    fun onClientTickPre(event: ClientTickEvent.Pre) {
        if (mc.screen != null || interactionAllowed()) return
        for (key in gatedVanillaKeys()) {
            while (key.consumeClick()) {
                // 丢弃：驾驶时E/Q/F是偏航/襟翼
            }
        }
    }
}
