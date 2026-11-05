// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.life.market

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Plain JVM market (no Minecraft types; `MarketTest`).
 *
 * The only designed number per item is its **initial price** ([ItemSpec.initialPrice]) at its **initial stock**
 * ([ItemSpec.depth]). After that nothing is pinned: the price is a function of the world supply, `price = P0 * S0 / S`
 * (a unit-elastic curve), so
 *  - a player **buying** takes units out of the supply and the price rises, **selling** puts units in and it falls
 *    (the amount paid is the integral under the curve, so a big order moves its own price: no free arbitrage);
 *  - every market [step] the supply grows on its own by a random amount `0 <= inc <= maxInc`. `maxInc` adjusts itself:
 *    `productionRate * (NATURAL_CAP * depth - stock)`, so a drained item restocks fast, a well-stocked one slowly, and
 *    none past `NATURAL_CAP` times its initial stock. `productionRate` is random per item (drawn once, then kept).
 *    Stock only falls when players buy, so prices fall by themselves over time and rise when players buy.
 *
 * The only guard rails are [MIN_STOCK_FRACTION] / [MAX_STOCK_FRACTION] of the initial stock (price at most
 * 1/MIN times, at least 1/MAX times the initial one) so a long random walk cannot reach 0 or infinity.
 */
data class ItemSpec(
    val id: String,
    /** First filter level ("group"): food / medical / ... in the general shop, missiles / shells / ... in the ammo shop. */
    val category: String,
    val initialPrice: Double,
    val depth: Double = DEFAULT_DEPTH,
    /** Which shop lists it: "general" or "ammo". */
    val shop: String = "general",
    /** Second filter level (caliber / gun / vehicle family) and third (round type: AP, HE, ...); blank = not used. */
    val sub: String = "",
    val kind: String = "",
) {
    init {
        require(initialPrice > 0 && depth > 0)
    }

    companion object {
        const val DEFAULT_DEPTH = 2000.0
    }
}

/** Mutable per-item state. [productionRate] is the item's random restocking speed (share of the gap to the natural cap per step). */
class ItemState(
    var stock: Double,
    val productionRate: Double,
    /** Recent prices, oldest first, one per step (for the chart). */
    val history: ArrayDeque<Double> = ArrayDeque(),
)

class Market(val specs: Map<String, ItemSpec>, val state: MutableMap<String, ItemState> = HashMap()) {

    companion object {
        const val MIN_STOCK_FRACTION = 0.05
        const val MAX_STOCK_FRACTION = 20.0

        /** The shop's margin: it pays this share less than it charges. */
        const val SPREAD = 0.06
        const val HISTORY = 48
        const val MAX_ORDER = 1000

        /** Range the per-item random production rate is drawn from. */
        const val RATE_MIN = 0.005
        const val RATE_MAX = 0.03

        /** Supply the market restocks towards on its own, times the initial stock (price at that point: half the initial one). */
        const val NATURAL_CAP = 2.0
    }

    /** Lists an item that is new to this market (random rates), or keeps what is saved. */
    fun ensure(spec: ItemSpec, random: java.util.Random) {
        state.getOrPut(spec.id) {
            ItemState(
                stock = spec.depth,
                productionRate = RATE_MIN + random.nextDouble() * (RATE_MAX - RATE_MIN),
            ).also { it.history.addLast(spec.initialPrice) }
        }
    }

    private fun spec(id: String) = specs[id]
    private fun st(id: String) = state[id]

    private fun minStock(s: ItemSpec) = s.depth * MIN_STOCK_FRACTION
    private fun maxStock(s: ItemSpec) = s.depth * MAX_STOCK_FRACTION

    /** Current price of one unit at the current supply. */
    fun price(id: String): Double? {
        val s = spec(id) ?: return null
        val t = st(id) ?: return null
        return s.initialPrice * s.depth / t.stock
    }

    /** What one more unit costs to buy / is paid for selling one, rounded to whole dollars (at least 1). */
    fun buyQuote(id: String): Long? = price(id)?.let { max(1L, ceil(it).toLong()) }
    fun sellQuote(id: String): Long? = price(id)?.let { max(1L, floor(it * (1 - SPREAD)).toLong()) }

    /** Units the shop can still sell (it never sells below the minimum stock). */
    fun available(id: String): Int {
        val s = spec(id) ?: return 0
        val t = st(id) ?: return 0
        return max(0, floor(t.stock - minStock(s)).toInt())
    }

    /** Units the market can still take before it is saturated. */
    fun capacity(id: String): Int {
        val s = spec(id) ?: return 0
        val t = st(id) ?: return 0
        return max(0, floor(maxStock(s) - t.stock).toInt())
    }

    /** Integral of the price curve over [a, b] with a < b: `P0*S0 * ln(b/a)`. */
    private fun area(s: ItemSpec, a: Double, b: Double) = s.initialPrice * s.depth * ln(b / a)

    /** Total to pay for [qty] units, or null if the item is unknown / not that many are available. */
    fun buyCost(id: String, qty: Int): Long? {
        if (qty !in 1..MAX_ORDER) return null
        val s = spec(id) ?: return null
        val t = st(id) ?: return null
        if (qty > available(id)) return null
        return max(qty.toLong(), ceil(area(s, t.stock - qty, t.stock)).toLong())
    }

    /** Total paid for [qty] units sold, or null if unknown / the market cannot take that many. */
    fun sellValue(id: String, qty: Int): Long? {
        if (qty !in 1..MAX_ORDER) return null
        val s = spec(id) ?: return null
        val t = st(id) ?: return null
        if (qty > capacity(id)) return null
        return max(qty.toLong(), floor(area(s, t.stock, t.stock + qty) * (1 - SPREAD)).toLong())
    }

    /** Executes a purchase of [qty]; returns what it cost, or null (nothing changed). */
    fun buy(id: String, qty: Int): Long? {
        val cost = buyCost(id, qty) ?: return null
        st(id)!!.stock -= qty
        return cost
    }

    /** Executes a sale of [qty]; returns what was paid out, or null (nothing changed). */
    fun sell(id: String, qty: Int): Long? {
        val value = sellValue(id, qty) ?: return null
        st(id)!!.stock += qty
        return value
    }

    /** Relative price change over the recorded history window (e.g. 0.05 = +5 %), 0 with no history. */
    fun change(id: String): Double {
        val t = st(id) ?: return 0.0
        val now = price(id) ?: return 0.0
        val first = t.history.firstOrNull() ?: return 0.0
        return now / first - 1.0
    }

    /**
     * One market tick: every item restocks by a random `0..maxInc` (see the class doc) and the price history advances.
     */
    fun step(random: java.util.Random) {
        for ((id, spec) in specs) {
            val t = st(id) ?: continue
            val maxInc = t.productionRate * max(0.0, NATURAL_CAP * spec.depth - t.stock)
            t.stock = min(maxStock(spec), t.stock + random.nextDouble() * maxInc)
            t.history.addLast(price(id)!!)
            while (t.history.size > HISTORY) t.history.removeFirst()
        }
    }
}
