// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life.core

/**
 * A job with a ladder of ranks. One shift is [shiftTicks] of "on duty" time (the server counts it while the player is
 * alive and online); each completed shift pays the current rank's wage into the bank and counts towards promotion.
 * Military jobs are the same thing with a harsher ladder: low starting pay, a long road to the top, and a court-martial
 * (see [Jobs.courtMartial]) for crimes against civilians. All numbers are DESIGN.
 */
data class Rank(val ko: String, val wagePerShift: Long, val shiftsToNext: Int)

data class JobDef(
    val id: String,
    val ko: String,
    val shiftTicks: Int,
    val ranks: List<Rank>,
    val military: Boolean = false,
    /** Jobs that police the others: a cop does not gain heat for hurting a player who is wanted. */
    val law: Boolean = false,
)

/** What the player does now: job id (blank = unemployed), rank index, shifts done at that rank, ticks into the shift. */
data class JobState(
    val jobId: String = "",
    val rank: Int = 0,
    val shifts: Int = 0,
    val shiftTicksDone: Int = 0,
) {
    val employed: Boolean get() = jobId.isNotEmpty()
}

object Jobs {
    private const val MINUTE = 20 * 60

    val ALL: List<JobDef> = listOf(
        JobDef(
            "courier", "택배 기사", 5 * MINUTE,
            listOf(Rank("견습", 120, 6), Rank("정규", 180, 10), Rank("팀장", 260, 0)),
        ),
        JobDef(
            "mechanic", "정비사", 5 * MINUTE,
            listOf(Rank("조수", 140, 6), Rank("정비사", 220, 10), Rank("수석 정비사", 330, 0)),
        ),
        JobDef(
            "medic", "응급 구조사", 5 * MINUTE,
            listOf(Rank("수습", 160, 8), Rank("구조사", 260, 12), Rank("구급 대장", 400, 0)),
        ),
        JobDef(
            "police", "경찰", 5 * MINUTE,
            listOf(Rank("순경", 200, 8), Rank("경장", 300, 12), Rank("경위", 450, 16), Rank("경감", 650, 0)),
            law = true,
        ),
        JobDef(
            "soldier", "군인", 5 * MINUTE,
            listOf(
                Rank("이병", 90, 4), Rank("일병", 120, 6), Rank("상병", 160, 8), Rank("병장", 210, 10),
                Rank("하사", 320, 14), Rank("중사", 440, 18), Rank("상사", 600, 24), Rank("소위", 820, 0),
            ),
            military = true,
        ),
    )

    private val byId = ALL.associateBy { it.id }

    fun find(id: String): JobDef? = byId[id]

    /** Result of advancing a shift counter: the new state, wage paid this step, and whether a promotion happened. */
    data class Step(val state: JobState, val wage: Long, val promoted: Boolean)

    fun join(state: JobState, id: String): JobState? {
        find(id) ?: return null
        // a soldier cannot just quit and rejoin to reset; leaving is [quit]
        return if (state.employed) null else JobState(jobId = id)
    }

    fun quit(@Suppress("UNUSED_PARAMETER") state: JobState): JobState = JobState()

    /** Advance by [ticks] of duty. May complete several shifts if [ticks] is large. */
    fun advance(state: JobState, ticks: Int): Step {
        val job = find(state.jobId) ?: return Step(state, 0, false)
        var s = state.copy(shiftTicksDone = state.shiftTicksDone + ticks)
        var wage = 0L
        var promoted = false
        while (s.shiftTicksDone >= job.shiftTicks) {
            val rank = job.ranks[s.rank.coerceIn(0, job.ranks.lastIndex)]
            wage += rank.wagePerShift
            val shifts = s.shifts + 1
            s = if (rank.shiftsToNext > 0 && shifts >= rank.shiftsToNext && s.rank < job.ranks.lastIndex) {
                promoted = true
                s.copy(rank = s.rank + 1, shifts = 0, shiftTicksDone = s.shiftTicksDone - job.shiftTicks)
            } else {
                s.copy(shifts = shifts, shiftTicksDone = s.shiftTicksDone - job.shiftTicks)
            }
        }
        return Step(s, wage, promoted)
    }

    /**
     * A soldier who commits a crime against a civilian is court-martialled: discharged and fined.
     * Returns null when [crime] does not apply (not military, or a crime against a combatant).
     */
    fun courtMartial(state: JobState, crime: Crime): Pair<JobState, Long>? {
        val job = find(state.jobId) ?: return null
        if (!job.military) return null
        if (crime != Crime.KILL_CIVILIAN && crime != Crime.KILL_PLAYER && crime != Crime.ASSAULT) return null
        return JobState() to 500L * (state.rank + 1)
    }
}
