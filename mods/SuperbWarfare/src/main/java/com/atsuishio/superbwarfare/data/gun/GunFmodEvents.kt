package com.atsuishio.superbwarfare.data.gun

/**
 * SBW 총기 id(레지스트리 path, 예: "ak_47") → DNCity가 번들한 FMOD `Weapons.bank`의 발사음
 * 이벤트 경로 매핑. 문자열 값은 TACZ의 `WeaponFmodEvents`(실제 총기 이름 기준 매핑, 뱅크 안의
 * 이벤트 경로를 직접 덤프해 만든 것)에서 실제 세계 이름이 겹치는 총기끼리 그대로 가져온 것이다.
 *
 * 서버(3인칭 브로드캐스트용 이벤트 경로 결정)와 클라이언트(1인칭 재생용) 양쪽에서 쓰이므로
 * `Dist` 제한이 없다 — 실제 FMOD 재생 자체는 client/sound/fmod의 클라이언트 전용 코드가 담당한다.
 */
object GunFmodEvents {

    /** 실제 이름이 겹치는(또는 충분히 가까운) 총기 전용 매핑. */
    private val GUN_SHOOT_EVENTS: Map<String, String> = mapOf(
        "ak_47" to "event:/Weapons/AK47/AK47 1P Shot",
        "rpk" to "event:/Weapons/AK47/AK47 1P Shot",
        "m_60" to "event:/Weapons/AK47/AK47 1P Shot",
        "hk_416" to "event:/Weapons/HK416/1P HK416 Fire",
        "m_4" to "event:/Weapons/M4A1/1P M4A1 Fire",
        "mk_14" to "event:/Weapons/M14/1P M14 Fire",
        "svd" to "event:/Weapons/M14/1P M14 Fire",
        "qbz_191" to "event:/Weapons/SR16/1P SR16 Fire",
        "qbz_95" to "event:/Weapons/SR16/1P SR16 Fire",
        "sks" to "event:/Weapons/SLR/1P SLR Fire",
        "glock_17" to "event:/Weapons/G19 New/1P G19 Fire",
        "glock_18" to "event:/Weapons/G19 New/1P G19 Fire",
        "m_1911" to "event:/Weapons/USP/1P USP Fire",
        "mp_443" to "event:/Weapons/M92FS/1P M92FS Fire",
        "aa_12" to "event:/Weapons/M1014/1P M1014 Fire",
        "m_870" to "event:/Weapons/870/1P 870 Fire",
        "homemade_shotgun" to "event:/Weapons/Nova/1P Nova Fire",
        "awm" to "event:/Weapons/M24/1P M24 Fire",
        "k_98" to "event:/Weapons/M24/1P M24 Fire",
        "mosin_nagant" to "event:/Weapons/M24/1P M24 Fire",
        "hunting_rifle" to "event:/Weapons/M24/1P M24 Fire",
        "m_98b" to "event:/Weapons/Grizzly/1P Grizzly Fire",
        "ntw_20" to "event:/Weapons/Grizzly/1P Grizzly Fire",
        "mp_5" to "event:/Weapons/MP5/1P MP5 Fire 1",
        "vector" to "event:/Weapons/MP9/1P MP9 Fire",
        "minigun" to "event:/Weapons/M249/3P M249 Fire",
        "devotion" to "event:/Weapons/M249/3P M249 Fire",
        "rpg" to "event:/Weapons/M32A1/1P M32A1 Fire",
        "m_79" to "event:/Weapons/M320/1P M320 Fire",
    )

    /** 위에서 매칭 안 된 총기가 쓸, GunType별 대표 발사음. */
    private val CATEGORY_DEFAULT_EVENTS: Map<GunType, String> = mapOf(
        GunType.RIFLE to "event:/Weapons/M4A1/1P M4A1 Fire",
        GunType.HANDGUN to "event:/Weapons/USP/1P USP Fire",
        GunType.SHOTGUN to "event:/Weapons/870/1P 870 Fire",
        GunType.SNIPER to "event:/Weapons/M24/1P M24 Fire",
        GunType.SMG to "event:/Weapons/MP5/1P MP5 Fire 1",
        GunType.MACHINE_GUN to "event:/Weapons/AK47/AK47 1P Shot",
        GunType.DIRECT_LAUNCHER to "event:/Weapons/M32A1/1P M32A1 Fire",
        GunType.CURVED_LAUNCHER to "event:/Weapons/M320/1P M320 Fire",
    )

    /**
     * 소음기 장착 사격용, 실제로 다른 음색으로 녹음된 발사음. Weapons.bank 전체 이벤트 경로를
     * 덤프해보면 소음기 전용 발사음은 이 두 개뿐이다 — `MP5SD`(일체형 소음기 SMG)와
     * `PB`(실존하는 소음 권총 마카로프 PB). 다른 모든 소음기 장착 총은 GunType상 더 가까운 쪽을
     * 재사용한다(RIFLE/SMG/MACHINE_GUN → MP5SD, HANDGUN → PB) — 원래 발사음을 볼륨만 낮춰
     * 재사용하는 방식은(귀로 확인함) 그냥 "조용해진 원래 총소리"일 뿐 실제 소음기 소리가 아니다.
     */
    private val SUPPRESSED_CATEGORY_EVENTS: Map<GunType, String> = mapOf(
        GunType.RIFLE to "event:/Weapons/MP5SD/1P MP5SD Fire",
        GunType.SMG to "event:/Weapons/MP5SD/1P MP5SD Fire",
        GunType.MACHINE_GUN to "event:/Weapons/MP5SD/1P MP5SD Fire",
        GunType.HANDGUN to "event:/Weapons/PB/1P PB Fire",
        GunType.SHOTGUN to "event:/Weapons/MP5SD/1P MP5SD Fire",
    )

    /**
     * @return 재생할 FMOD 이벤트 경로, 또는 `null`(이 총은 FMOD 미대상 — [GunType.SPECIAL]이거나
     * 어떤 대표 이벤트도 정의되지 않은 카테고리).
     */
    @JvmStatic
    fun shootEventFor(gunId: String, gunType: GunType): String? {
        if (gunType == GunType.SPECIAL) return null
        return GUN_SHOOT_EVENTS[gunId] ?: CATEGORY_DEFAULT_EVENTS[gunType]
    }

    /** [SUPPRESSED_CATEGORY_EVENTS]의 doc 참고. */
    @JvmStatic
    fun suppressedShootEventFor(gunType: GunType): String? {
        if (gunType == GunType.SPECIAL) return null
        return SUPPRESSED_CATEGORY_EVENTS[gunType]
    }
}
