// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life

import io.github.jwyoon1220.dncity.life.core.Account
import io.github.jwyoon1220.dncity.life.core.Crime
import io.github.jwyoon1220.dncity.life.core.Economy
import io.github.jwyoon1220.dncity.life.core.JobState
import io.github.jwyoon1220.dncity.life.core.Jobs
import io.github.jwyoon1220.dncity.life.core.Pool
import io.github.jwyoon1220.dncity.life.core.Wanted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LifeCoreTest {
    @Test
    fun `deposit and withdraw move money and refuse overdrafts`() {
        val a = Account(cash = 100)
        assertEquals(Account(40, 60), a.deposit(60))
        assertNull(a.deposit(101))
        assertNull(a.deposit(0))
        assertEquals(Account(130, 0), Account(100, 30).withdraw(30))
        assertNull(Account(100, 30).withdraw(31))
    }

    @Test
    fun `admin money set and adjust never go below zero`() {
        val a = Account(100, 200)
        assertEquals(Account(5000, 200), a.with(Pool.CASH, 5000))
        assertEquals(Account(100, 0), a.with(Pool.BANK, -5))
        assertEquals(Account(100, 250), a.adjust(Pool.BANK, 50))
        assertEquals(Account(0, 200), a.adjust(Pool.CASH, -999))
        assertEquals(Account(100, 150), a.adjust(Pool.BANK, -50))
    }

    @Test
    fun `charge takes cash first then bank`() {
        assertEquals(Account(0, 70), Account(30, 100).charge(60))
        assertNull(Account(30, 100).charge(131))
    }

    @Test
    fun `death loses a quarter of cash but never the bank`() {
        val d = Economy.onDeath(Account(cash = 1000, bank = 5000))
        assertEquals(250, d.dropped)
        assertEquals(Account(750, 5000), d.after)
    }

    @Test
    fun `hospital bill is waived beyond what the player owns`() {
        val (after, paid) = Economy.hospitalBill(Account(20, 30))
        assertEquals(50, paid)
        assertEquals(Account(0, 0), after)
        assertEquals(Economy.HOSPITAL_FEE, Economy.hospitalBill(Account(1000, 0)).second)
    }

    @Test
    fun `transfer needs cash on hand`() {
        assertNull(Economy.transfer(Account(10), Account(), 11))
        assertNull(Economy.transfer(Account(10), Account(), -5))
        val (f, t) = Economy.transfer(Account(10), Account(5), 4)!!
        assertEquals(6, f.cash)
        assertEquals(9, t.cash)
    }

    @Test
    fun `crimes add heat and stars follow thresholds`() {
        var w = Wanted()
        assertEquals(0, w.stars)
        w = w.commit(Crime.ASSAULT)
        assertEquals(1, w.stars)
        w = w.commit(Crime.KILL_POLICE)
        assertEquals(4, w.stars) // 25 + 180 = 205 >= 200
        repeat(5) { w = w.commit(Crime.KILL_POLICE) }
        assertEquals(5, w.stars)
        assertEquals(Wanted.MAX_HEAT, w.heat)
    }

    @Test
    fun `heat holds during the delay then cools, and witnesses stop cooling`() {
        var w = Wanted().commit(Crime.KILL_CIVILIAN)
        w = w.tick(Wanted.COOL_DELAY_TICKS - 20, witnessed = false)
        assertEquals(Crime.KILL_CIVILIAN.heat, w.heat)
        w = w.tick(20 * 10, witnessed = false)
        assertTrue(w.heat < Crime.KILL_CIVILIAN.heat)
        val seen = w.tick(20 * 60, witnessed = true)
        assertEquals(w.heat, seen.heat)
        assertEquals(0, seen.ticksSinceCrime)
    }

    @Test
    fun `heat reaches zero and stays there`() {
        var w = Wanted().commit(Crime.ASSAULT)
        w = w.tick(20 * 3600, witnessed = false)
        assertEquals(0.0, w.heat)
        assertEquals(w, w.tick(100, witnessed = false))
    }

    @Test
    fun `bail is per star`() {
        assertEquals(3 * Economy.BAIL_PER_STAR, Wanted(heat = 130.0).bail())
    }

    @Test
    fun `a shift pays the rank wage and promotion resets the counter`() {
        val job = Jobs.find("courier")!!
        var s = Jobs.join(JobState(), "courier")!!
        var total = 0L
        repeat(job.ranks[0].shiftsToNext) {
            val step = Jobs.advance(s, job.shiftTicks)
            s = step.state
            total += step.wage
        }
        assertEquals(1, s.rank)
        assertEquals(0, s.shifts)
        assertEquals(job.ranks[0].wagePerShift * job.ranks[0].shiftsToNext, total)
    }

    @Test
    fun `partial shifts accumulate and a long gap pays several shifts`() {
        val job = Jobs.find("police")!!
        var s = JobState("police")
        val half = Jobs.advance(s, job.shiftTicks / 2)
        assertEquals(0, half.wage)
        s = half.state
        val rest = Jobs.advance(s, job.shiftTicks / 2 + job.shiftTicks * 2)
        assertEquals(job.ranks[0].wagePerShift * 3, rest.wage)
    }

    @Test
    fun `top rank keeps earning without promotion`() {
        val job = Jobs.find("medic")!!
        val top = JobState("medic", rank = job.ranks.lastIndex)
        val step = Jobs.advance(top, job.shiftTicks)
        assertEquals(job.ranks.last().wagePerShift, step.wage)
        assertFalse(step.promoted)
        assertEquals(job.ranks.lastIndex, step.state.rank)
    }

    @Test
    fun `cannot hold two jobs or an unknown one`() {
        assertNull(Jobs.join(JobState("police"), "medic"))
        assertNull(Jobs.join(JobState(), "astronaut"))
    }

    @Test
    fun `soldiers are court-martialled for killing civilians, not police`() {
        val soldier = JobState("soldier", rank = 3)
        val (state, fine) = assertNotNull(Jobs.courtMartial(soldier, Crime.KILL_CIVILIAN))
        assertFalse(state.employed)
        assertEquals(2000, fine)
        assertNull(Jobs.courtMartial(soldier, Crime.KILL_POLICE))
        assertNull(Jobs.courtMartial(JobState("courier"), Crime.KILL_CIVILIAN))
    }

    @Test
    fun `every job has a rank ladder that ends`() {
        for (job in Jobs.ALL) {
            assertTrue(job.ranks.isNotEmpty())
            assertEquals(0, job.ranks.last().shiftsToNext, job.id)
            job.ranks.dropLast(1).forEach { assertTrue(it.shiftsToNext > 0, job.id) }
            // pay never drops on promotion
            job.ranks.zipWithNext().forEach { (a, b) -> assertTrue(b.wagePerShift >= a.wagePerShift, job.id) }
        }
    }
}
