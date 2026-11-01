package com.atsuishio.superbwarfare.network.message.receive

import com.atsuishio.superbwarfare.client.sound.fmod.SbwFmodWeaponSoundManager
import com.atsuishio.superbwarfare.data.gun.GunType
import com.atsuishio.superbwarfare.network.ClientPacketPayload
import com.atsuishio.superbwarfare.network.PayloadContext
import com.atsuishio.superbwarfare.tools.clientLevel
import kotlinx.serialization.Serializable

/**
 * 총을 쏜 사람 본인을 제외한, 그 엔티티를 추적 중인 근처 클라이언트들에게 3인칭 FMOD 발사음을
 * 재생하라고 알리는 패킷. 쏜 사람 본인은 [com.atsuishio.superbwarfare.event.ClientEventHandler]의
 * 로컬 발사 예측에서 이미 자신의 클라이언트에 직접 재생했으므로 이 패킷을 받지 않는다
 * (`PacketDistributor.sendToPlayersTrackingEntity`는 엔티티 자신의 소유 클라이언트를 "추적자"로
 * 취급하지 않는다).
 */
@Serializable
data class GunFireFmodSoundMessage(
    val shooterId: Int,
    val eventPath: String,
    val silenced: Boolean = false,
    val gunType: GunType? = null,
) : ClientPacketPayload() {

    override fun PayloadContext.handler() {
        val entity = clientLevel?.getEntity(shooterId) ?: return
        SbwFmodWeaponSoundManager.playAt(eventPath, entity, silenced, gunType)
    }
}
