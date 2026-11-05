// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.life.market

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketTest {
    private val bread = ItemSpec("minecraft:bread", "food", 10.0, depth = 1000.0)
    private val ammo = ItemSpec("dncity:a", "ammo", 2.0, depth = 5000.0)

    private fun market(vararg specs: ItemSpec, seed: Long = 1): Pair<Market, Random> {
        val m = Market(specs.associateBy { it.id })
        val r = Random(seed)
        specs.forEach { m.ensure(it, r) }
        return m to r
    }

    @Test
    fun `a fresh item trades at its initial price`() {
        val (m, _) = market(bread)
        assertEquals(10.0, m.price(bread.id)!!, 1e-9)
        assertEquals(10L, m.buyQuote(bread.id))
        assertEquals(9L, m.sellQuote(bread.id)) // 6 % spread, rounded down
    }

    @Test
    fun `buying raises the price and selling lowers it`() {
        val (m, _) = market(bread)
        val p0 = m.price(bread.id)!!
        m.buy(bread.id, 200)
        val p1 = m.price(bread.id)!!
        assertTrue(p1 > p0)
        m.sell(bread.id, 400)
        assertTrue(m.price(bread.id)!! < p0)
    }

    @Test
    fun `a bigger order costs more per unit than the quote`() {
        val (m, _) = market(bread)
        val cost = m.buyCost(bread.id, 300)!!
        assertTrue(cost > 300 * 10, "cost $cost")
        // but less than paying the final price for every unit
        val endPrice = 10.0 * 1000 / 700
        assertTrue(cost < 300 * endPrice)
    }

    @Test
    fun `buying then selling the same amount always loses money`() {
        val (m, _) = market(bread)
        val paid = m.buy(bread.id, 150)!!
        val got = m.sell(bread.id, 150)!!
        assertTrue(got < paid, "paid $paid got $got")
        // and the supply is back where it was
        assertEquals(10.0, m.price(bread.id)!!, 1e-9)
    }

    @Test
    fun `orders beyond supply or capacity are refused without changing anything`() {
        val (m, _) = market(bread)
        val avail = m.available(bread.id)
        assertEquals(950, avail) // minimum stock is 5 % of 1000
        assertNull(m.buyCost(bread.id, avail + 1))
        assertNull(m.buy(bread.id, avail + 1))
        assertEquals(10.0, m.price(bread.id)!!, 1e-9)
        assertNull(m.sellValue(bread.id, 0))
        assertNull(m.sellValue(bread.id, Market.MAX_ORDER + 1))
        assertNull(m.buyCost("nope", 1))
    }

    @Test
    fun `production rate is random per item, inside the range, and stays fixed`() {
        val (m, _) = market(bread, ammo, seed = 7)
        val a = m.state[bread.id]!!
        val b = m.state[ammo.id]!!
        for (t in listOf(a, b)) assertTrue(t.productionRate in Market.RATE_MIN..Market.RATE_MAX)
        assertNotEquals(a.productionRate, b.productionRate)
        val before = a.productionRate
        repeat(20) { m.step(Random(it.toLong())) }
        assertEquals(before, a.productionRate)
    }

    @Test
    fun `stock only grows during steps, by 0 up to the self-adjusting maximum`() {
        val (m, r) = market(bread, seed = 3)
        val t = m.state[bread.id]!!
        repeat(200) {
            val before = t.stock
            val maxInc = t.productionRate * maxOf(0.0, Market.NATURAL_CAP * bread.depth - before)
            m.step(r)
            val inc = t.stock - before
            assertTrue(inc >= 0.0, "stock fell by $inc")
            assertTrue(inc <= maxInc + 1e-9, "inc $inc > max $maxInc")
        }
    }

    @Test
    fun `a drained item restocks faster than a full one`() {
        fun avgInc(stock: Double): Double {
            val (m, _) = market(bread, seed = 5)
            val t = m.state[bread.id]!!
            var total = 0.0
            repeat(2000) {
                t.stock = stock
                m.step(Random(it.toLong()))
                total += t.stock - stock
            }
            return total / 2000
        }
        assertTrue(avgInc(100.0) > avgInc(1500.0))
        assertEquals(0.0, avgInc(2000.0), 1e-9) // at the natural cap nothing is added
    }

    @Test
    fun `left alone the market settles near the natural cap and never above it`() {
        val (m, r) = market(bread, seed = 11)
        repeat(5_000) { m.step(r) }
        val stock = m.state[bread.id]!!.stock
        assertTrue(stock <= Market.NATURAL_CAP * bread.depth + 1e-6)
        assertTrue(stock > 1.9 * bread.depth)
        assertEquals(10.0 / Market.NATURAL_CAP, m.price(bread.id)!!, 0.6)
    }

    @Test
    fun `a drained item recovers by itself over time`() {
        val (m, r) = market(bread, seed = 2)
        m.buy(bread.id, m.available(bread.id))
        val drained = m.price(bread.id)!!
        repeat(120) { m.step(r) }
        assertTrue(m.price(bread.id)!! < drained / 2)
    }

    @Test
    fun `history is capped and starts with the initial price`() {
        val (m, r) = market(bread)
        assertEquals(10.0, m.state[bread.id]!!.history.first())
        repeat(200) { m.step(r) }
        assertEquals(Market.HISTORY, m.state[bread.id]!!.history.size)
        assertNotNull(m.change(bread.id))
    }

    @Test
    fun `same seed gives the same market`() {
        fun run(): Double {
            val (m, r) = market(bread, ammo, seed = 42)
            repeat(500) { m.step(r) }
            return m.price(bread.id)!!
        }
        assertEquals(run(), run())
    }

    @Test
    fun `selling floods the market and a drained item gets expensive`() {
        val (m, _) = market(bread)
        m.buy(bread.id, m.available(bread.id))
        assertEquals(0, m.available(bread.id))
        assertEquals(200L, m.buyQuote(bread.id)) // 5 % stock = 20x the initial price
    }
}
