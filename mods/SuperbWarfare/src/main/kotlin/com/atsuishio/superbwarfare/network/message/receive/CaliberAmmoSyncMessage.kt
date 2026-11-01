package com.atsuishio.superbwarfare.network.message.receive

import com.atsuishio.superbwarfare.init.ModAttachments
import com.atsuishio.superbwarfare.network.ClientPacketPayload
import com.atsuishio.superbwarfare.network.PayloadContext
import com.atsuishio.superbwarfare.tools.clientLevel
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation

/**
 * 同步玩家的 TACZ 口径弹药池（[com.atsuishio.superbwarfare.capability.player.PlayerVariable.caliberAmmo]）。
 * data 的 key 为弹药口径 ResourceLocation 的字符串形式（如 "tacz:556x45"）。
 */
@Serializable
data class CaliberAmmoSyncMessage(
    val target: Int,
    val data: Map<String, Int>,
) : ClientPacketPayload() {

    override fun PayloadContext.handler() {
        val entity = clientLevel?.getEntity(target) ?: return

        val variables = entity.getData(ModAttachments.PLAYER_VARIABLE)

        for ((key, value) in data) {
            val id = ResourceLocation.tryParse(key) ?: continue
            variables.setCaliberAmmo(id, value)
        }

        entity.setData(ModAttachments.PLAYER_VARIABLE, variables)
    }
}
