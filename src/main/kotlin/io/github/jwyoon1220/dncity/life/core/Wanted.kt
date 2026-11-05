// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life.core

/** A crime and the heat it adds. Heat is a score; [Wanted.stars] maps it to 0..5 stars. DESIGN values. */
enum class Crime(val heat: Double, val ko: String) {
    ASSAULT(25.0, "폭행"),
    KILL_CIVILIAN(70.0, "민간인 살해"),
    KILL_PLAYER(90.0, "플레이어 살해"),
    KILL_POLICE(180.0, "경찰 살해"),
    ATTACK_POLICE(60.0, "경찰 공격"),
    VEHICLE_THEFT(35.0, "차량 절도"),
    GUNFIRE(15.0, "총기 난사"),
    EXPLOSION(50.0, "폭발물 사용"),
}

/**
 * Heat -> stars -> decay. Pure and tick-driven: the caller passes elapsed ticks, nothing reads a clock.
 * Heat only starts to cool after [COOL_DELAY_TICKS] without a new crime, then falls [DECAY_PER_SECOND] per second;
 * being seen by police ([witnessed]) holds the cooling off.
 */
data class Wanted(val heat: Double = 0.0, val ticksSinceCrime: Int = 0) {

    val stars: Int get() = THRESHOLDS.count { heat >= it }

    fun commit(crime: Crime): Wanted = copy(heat = (heat + crime.heat).coerceAtMost(MAX_HEAT), ticksSinceCrime = 0)

    /** Advance by [ticks] game ticks. */
    fun tick(ticks: Int, witnessed: Boolean): Wanted {
        if (heat <= 0.0) return this
        val quiet = if (witnessed) 0 else ticksSinceCrime + ticks
        if (quiet < COOL_DELAY_TICKS) return copy(ticksSinceCrime = quiet)
        // only the part of this step past the delay cools
        val coolingTicks = minOf(ticks, quiet - COOL_DELAY_TICKS)
        return copy(heat = (heat - DECAY_PER_SECOND * coolingTicks / 20.0).coerceAtLeast(0.0), ticksSinceCrime = quiet)
    }

    /** Busted: bail is charged and the record is wiped. */
    fun bail(): Long = stars * Economy.BAIL_PER_STAR

    companion object {
        /** Heat needed for 1..5 stars. */
        val THRESHOLDS = doubleArrayOf(20.0, 60.0, 120.0, 200.0, 320.0)
        const val MAX_HEAT = 400.0
        const val COOL_DELAY_TICKS = 20 * 20
        const val DECAY_PER_SECOND = 2.0
    }
}
