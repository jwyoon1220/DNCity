// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life.core

/**
 * Plain JVM economy rules (no Minecraft types, so `LifeCoreTest` runs without the game). Money is whole dollars.
 * All numbers here are DESIGN values: nothing sourced, tune freely.
 */
data class Account(val cash: Long = 0, val bank: Long = 0) {
    init {
        require(cash >= 0 && bank >= 0) { "negative balance" }
    }

    fun withCash(delta: Long): Account? = (cash + delta).takeIf { it >= 0 }?.let { copy(cash = it) }

    /** Cash -> bank. Null if there is not enough cash. */
    fun deposit(amount: Long): Account? =
        if (amount <= 0 || amount > cash) null else copy(cash = cash - amount, bank = bank + amount)

    /** Bank -> cash. Null if there is not enough in the bank. */
    fun withdraw(amount: Long): Account? =
        if (amount <= 0 || amount > bank) null else copy(cash = cash + amount, bank = bank - amount)

    /** Wage / reward straight into the bank (payroll is a transfer, it never makes the player carry cash). */
    fun credit(amount: Long): Account = if (amount <= 0) this else copy(bank = bank + amount)

    /** Admin tools: set one of the two balances to [value] (>= 0). */
    fun with(pool: Pool, value: Long): Account =
        if (pool == Pool.CASH) copy(cash = value.coerceAtLeast(0)) else copy(bank = value.coerceAtLeast(0))

    /** Admin tools: change one balance by [delta]; a balance never goes below 0 (taking more than there is empties it). */
    fun adjust(pool: Pool, delta: Long): Account = with(pool, (if (pool == Pool.CASH) cash else bank) + delta)

    /** Pays [amount] from cash first, then from the bank. Null if the two together cannot cover it. */
    fun charge(amount: Long): Account? {
        if (amount < 0 || amount > cash + bank) return null
        val fromCash = minOf(cash, amount)
        return copy(cash = cash - fromCash, bank = bank - (amount - fromCash))
    }
}

enum class Pool { CASH, BANK }

object Economy {
    const val STARTING_CASH = 500L

    /** Share of carried cash lost on death (a killer who is a player picks it up). The bank is safe. */
    const val DEATH_CASH_LOSS = 0.25

    /** Flat hospital bill, charged after respawn (cash first, then bank; whatever cannot be paid is waived). */
    const val HOSPITAL_FEE = 150L

    /** Bail is 1000 per wanted star, charged when the player is "busted" (see [Wanted]). */
    const val BAIL_PER_STAR = 1000L

    data class Death(val after: Account, val dropped: Long)

    /** What dying does to the wallet. */
    fun onDeath(a: Account): Death {
        val dropped = (a.cash * DEATH_CASH_LOSS).toLong()
        return Death(a.copy(cash = a.cash - dropped), dropped)
    }

    /** Hospital bill after respawn: as much of [HOSPITAL_FEE] as the player can pay. Returns the new account and what was paid. */
    fun hospitalBill(a: Account): Pair<Account, Long> {
        val paid = minOf(HOSPITAL_FEE, a.cash + a.bank)
        return (a.charge(paid) ?: a) to paid
    }

    /** Pays a player-to-player transfer. Null when the sender cannot cover it or the amount is not positive. */
    fun transfer(from: Account, to: Account, amount: Long): Pair<Account, Account>? {
        if (amount <= 0) return null
        val f = from.withCash(-amount) ?: return null
        return f to to.withCash(amount)!!
    }
}
