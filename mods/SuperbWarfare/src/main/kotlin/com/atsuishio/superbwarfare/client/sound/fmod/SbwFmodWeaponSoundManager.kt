package com.atsuishio.superbwarfare.client.sound.fmod

import com.atsuishio.superbwarfare.Mod
import com.atsuishio.superbwarfare.data.gun.GunFmodEvents
import com.atsuishio.superbwarfare.data.gun.GunType
import com.iwei20.fmod.FModLoad
import com.iwei20.fmod.studio.FMODException
import com.iwei20.fmod.studio.FMODSystem
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * SBW 전용 클라이언트 FMOD Studio 시스템. TACZ의 `FmodWeaponSoundManager`와 같은 패턴(같은
 * DNCity 번들 뱅크, 같은 지연 초기화/폴백)을 쓰지만, TACZ의 인스턴스를 공유하지 않고 완전히
 * 독립된 자체 `FMODSystem`을 쓴다 — TACZ 클래스가 package-private이라 접근 불가능하기도 하고,
 * `/music`의 `AudioPlayer`가 이미 같은 이유(그 기능이 TACZ 설치 여부에 의존하지 않도록)로
 * TACZ의 Studio 시스템과 독립된 두 번째 FMOD 인스턴스를 쓰는 것과 같은 전례를 따른다.
 *
 * 초기화는 best-effort — 네이티브 로딩/뱅크 로딩/시스템 생성 중 무엇이 실패해도 경고만 남기고
 * 이후 모든 재생 호출이 조용히 no-op된다(게임이 죽지 않음).
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(Dist.CLIENT, modid = Mod.MODID)
object SbwFmodWeaponSoundManager {
    private const val BANK_SOURCE_MOD_ID = "dncity"
    private const val MASTER_BANK_RESOURCE = "fmod/Master.bank"
    private const val MASTER_STRINGS_BANK_RESOURCE = "fmod/Master.strings.bank"
    private const val WEAPONS_BANK_RESOURCE = "fmod/Weapons.bank"

    private var initAttempted = false
    private var system: FMODSystem? = null

    private const val SHOOT_VOLUME_MULTIPLIER = 4.0f

    // 소음기 발사음은 실제로 다른(더 조용한) 녹음/파라미터를 쓰므로 SHOOT_VOLUME_MULTIPLIER의
    // 과격한 증폭이 필요 없다 — 다른 게임 소리에 묻히지 않을 정도로만 살짝 키운다.
    private const val SUPPRESSED_SHOOT_VOLUME_MULTIPLIER = 1.5f

    // 뱅크 자체의 실제 오타(P 하나) — 뱅크와 정확히 맞춰야 하므로 우리 쪽에서 "고치지" 않는다.
    private const val IS_SUPPRESSED_PARAM = "IsSupressed"

    @Synchronized
    private fun ensureInitialized() {
        if (initAttempted) return
        initAttempted = true

        try {
            FModLoad.load()

            val newSystem = FMODSystem()
            // Master.bank가 이 빌드가 갖고 있지 않은 DSP/플러그인을 참조해서, 이 플래그 없이는
            // 뱅크 로드 전체가 FMOD_ERR_OUTPUT_INIT으로 실패한다 (TACZ FmodWeaponSoundManager와 동일).
            newSystem.initialize(FMODSystem.InitFlags.AllowMissingPlugins.constant)

            val bankDir = java.nio.file.Files.createTempDirectory("dncity-fmod-banks-sbw-")
            bankDir.toFile().deleteOnExit()

            val masterBankFile = SbwFmodBankLocator.extractBank(BANK_SOURCE_MOD_ID, MASTER_BANK_RESOURCE, bankDir)
            masterBankFile.toFile().deleteOnExit()
            newSystem.loadBankFile(masterBankFile.toString())

            val stringsBankFile =
                SbwFmodBankLocator.extractBank(BANK_SOURCE_MOD_ID, MASTER_STRINGS_BANK_RESOURCE, bankDir)
            stringsBankFile.toFile().deleteOnExit()
            newSystem.loadBankFile(stringsBankFile.toString())

            val weaponsBankFile = SbwFmodBankLocator.extractBank(BANK_SOURCE_MOD_ID, WEAPONS_BANK_RESOURCE, bankDir)
            weaponsBankFile.toFile().deleteOnExit()
            val weaponsBank = newSystem.loadBankFile(weaponsBankFile.toString())
            weaponsBank.loadSampleData()

            system = newSystem
            Mod.LOGGER.info(
                "[SBW FMOD] Weapon sound system initialized ({}, {}, {})",
                MASTER_BANK_RESOURCE, MASTER_STRINGS_BANK_RESOURCE, WEAPONS_BANK_RESOURCE
            )
        } catch (e: Exception) {
            // IOException | FMODException | UnsupportedOperationException | UnsatisfiedLinkError
            Mod.LOGGER.warn("[SBW FMOD] Failed to initialize weapon sound system; falling back to vanilla gun sounds.", e)
            system = null
        }
    }

    /** [eventPath]의 FMOD 이벤트를 [position]에서 1회 재생한다(fire-and-forget). `IsOutside`는 알 수 없으므로 설정하지 않는다 — [entity] 버전을 우선 사용할 것. */
    fun playAt(eventPath: String, position: Vec3) {
        ensureInitialized()
        val sys = system ?: return

        try {
            val instance = sys.getEvent(eventPath).createInstance()
            instance.set3DAttributes(
                FMODSystem.Attributes3D.stationary(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
            )
            instance.start()
            instance.release()
        } catch (e: FMODException) {
            Mod.LOGGER.warn("[SBW FMOD] Failed to play event {}", eventPath, e)
        }
    }

    /**
     * [eventPath]의 FMOD 이벤트를 [entity]의 현재 위치에서 1회 재생한다. TACZ의
     * `FmodWeaponSoundManager.playShoot`와 동일하게, 이벤트가 `IsOutside` 파라미터를 정의하면
     * (뱅크의 발사음 어쿠스틱이 이 값에 따라 달라짐 — 리버브 send/tail 레이어) `entity.level().canSeeSky(...)`
     * 로 실외 여부를 설정한다. 이 메서드는 1인칭(로컬 발사 예측, `ClientEventHandler`)과 3인칭
     * (서버가 브로드캐스트한 `GunFireFmodSoundMessage`) 양쪽 호출 경로가 공유하므로, 이 한 곳만
     * 고치면 자신이 쏠 때와 남이 쏘는 걸 들을 때 모두 TACZ와 같은 실내/실외 음향을 얻는다.
     *
     * [silenced]가 true면: [eventPath](항상 원래 총소리 이벤트 — 뱅크 전체 이벤트 경로를 덤프해보면
     * 대부분의 총이 자기 Fire 이벤트에 실제로 `IsSupressed`라는(뱅크 자체 오타, P 하나) 파라미터를
     * 갖고 있어서, 그 총 고유의 진짜 소음기 소리를 이미 갖고 있다) 자체에 그 파라미터가 있으면 그걸
     * 1로 켠다. 없는 극소수 총만(예: 샷건 대표값으로 쓰는 Nova) [gunType]으로
     * `GunFmodEvents.suppressedShootEventFor`가 고르는, 실제로 다른 총(MP5SD/PB)의 소음기
     * 발사음으로 대체한다.
     */
    fun playAt(eventPath: String, entity: Entity, silenced: Boolean = false, gunType: GunType? = null) {
        ensureInitialized()
        val sys = system ?: return

        try {
            var description = sys.getEvent(eventPath)
            var params = description.parameterNames

            val hasOwnSuppressedLayer = silenced && params.contains(IS_SUPPRESSED_PARAM)
            if (silenced && !hasOwnSuppressedLayer && gunType != null) {
                val fallback = GunFmodEvents.suppressedShootEventFor(gunType)
                if (fallback != null) {
                    description = sys.getEvent(fallback)
                    params = description.parameterNames
                }
            }

            val instance = description.createInstance()
            val position = entity.position()
            instance.set3DAttributes(
                FMODSystem.Attributes3D.stationary(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
            )
            instance.setVolume(if (silenced) SUPPRESSED_SHOOT_VOLUME_MULTIPLIER else SHOOT_VOLUME_MULTIPLIER)
            if (params.contains("IsOutside")) {
                val outside = entity.level().canSeeSky(entity.blockPosition())
                instance.setParameterByName("IsOutside", if (outside) 1f else 0f)
            }
            if (hasOwnSuppressedLayer) {
                instance.setParameterByName(IS_SUPPRESSED_PARAM, 1f)
            }
            instance.start()
            instance.release()
        } catch (e: FMODException) {
            Mod.LOGGER.warn("[SBW FMOD] Failed to play event {} for entity", eventPath, e)
        }
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val sys = system ?: return

        val minecraft = Minecraft.getInstance()
        if (minecraft.level != null) {
            val camera = minecraft.gameRenderer.mainCamera
            val pos = camera.position
            val look = camera.lookVector
            val up = camera.upVector
            sys.setListenerAttributes(
                0,
                FMODSystem.Attributes3D(
                    FMODSystem.Vector3(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
                    FMODSystem.Vector3.ZERO,
                    FMODSystem.Vector3(look.x(), look.y(), look.z()),
                    FMODSystem.Vector3(up.x(), up.y(), up.z())
                )
            )
        }

        try {
            sys.update()
        } catch (e: FMODException) {
            Mod.LOGGER.warn("[SBW FMOD] System update failed", e)
        }
    }
}
