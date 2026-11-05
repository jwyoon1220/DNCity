// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.life.market

import com.google.gson.JsonParser
import io.github.jwyoon1220.dncity.Dncity
import io.github.jwyoon1220.dncity.life.Life
import io.github.jwyoon1220.dncity.network.ShopEntry
import io.github.jwyoon1220.dncity.network.ShopSnapshotPayload
import net.minecraft.ChatFormatting
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.util.Random

/** The world's market state, saved in the overworld's data storage so prices survive restarts. */
class MarketSavedData : SavedData() {
    val items = HashMap<String, ItemState>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val out = CompoundTag()
        for ((id, s) in items) {
            val t = CompoundTag()
            t.putDouble("stock", s.stock)
            t.putDouble("production", s.productionRate)
            val h = ListTag()
            s.history.forEach { h.add(DoubleTag.valueOf(it)) }
            t.put("history", h)
            out.put(id, t)
        }
        tag.put("items", out)
        return tag
    }

    companion object {
        const val NAME = "dncity_market"

        fun load(tag: CompoundTag, @Suppress("UNUSED_PARAMETER") registries: HolderLookup.Provider): MarketSavedData {
            val d = MarketSavedData()
            val items = tag.getCompound("items")
            for (id in items.allKeys) {
                val t = items.getCompound(id)
                val s = ItemState(
                    t.getDouble("stock"),
                    // worlds saved before the restocking model stored "rise": keep it as the production rate
                    if (t.contains("production")) t.getDouble("production") else t.getDouble("rise").coerceIn(Market.RATE_MIN, Market.RATE_MAX),
                )
                t.getList("history", Tag.TAG_DOUBLE.toInt()).forEach { s.history.addLast((it as DoubleTag).asDouble) }
                if (s.stock > 0) d.items[id] = s
            }
            return d
        }

        val FACTORY: Factory<MarketSavedData> = Factory({ MarketSavedData() }, ::load, null)
    }
}

/**
 * Server side of the shop: owns the [Market], steps it, and carries out trades. The shop UI is
 * [io.github.jwyoon1220.dncity.client.shop.ShopFragment]; every trade is re-validated here (price, stock, money, items).
 * Prices come only from the market ([Market]); `market_items.json` just lists items with an initial price.
 */
object MarketService {
    /** One market step per minute of game time. */
    private const val STEP_TICKS = 20 * 60
    private const val RESOURCE = "/dncity/market_items.json"

    private var market: Market? = null
    private var data: MarketSavedData? = null

    private fun loadSpecs(): List<ItemSpec> {
        val stream = MarketService::class.java.getResourceAsStream(RESOURCE) ?: error("missing $RESOURCE")
        val root = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        return root.getAsJsonArray("items").mapNotNull { e ->
            val o = e.asJsonObject
            val id = o["id"].asString
            // an item of a mod that is not installed is simply not listed
            if (!MarketItems.exists(id)) return@mapNotNull null
            ItemSpec(
                id, o["category"].asString, o["price"].asDouble, o["depth"]?.asDouble ?: ItemSpec.DEFAULT_DEPTH,
                shop = o["shop"]?.asString ?: "general", sub = o["sub"]?.asString ?: "", kind = o["kind"]?.asString ?: "",
            )
        }
    }

    fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        val saved = server.overworld().dataStorage.computeIfAbsent(MarketSavedData.FACTORY, MarketSavedData.NAME)
        val specs = loadSpecs()
        val m = Market(specs.associateBy { it.id }, saved.items)
        val seed = server.overworld().seed
        // rates are random, drawn once per item per world (then saved)
        specs.forEach { m.ensure(it, Random(seed xor it.id.hashCode().toLong())) }
        market = m
        data = saved
        saved.setDirty()
        Dncity.LOGGER.info("Market: {} items listed", specs.size)
    }

    fun onServerStopped(@Suppress("UNUSED_PARAMETER") event: ServerStoppedEvent) {
        market = null
        data = null
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        val m = market ?: return
        if (event.server.tickCount % STEP_TICKS != 0) return
        m.step(Random())
        data?.setDirty()
    }

    // ---------------------------------------------------------------- UI protocol

    const val SHOP_GENERAL = "general"
    const val SHOP_AMMO = "ammo"

    /** Listing order is the catalog's (the UI makes its tabs in first-seen order), not sorted. */
    fun snapshot(player: ServerPlayer, shop: String): ShopSnapshotPayload? {
        val m = market ?: return null
        val life = Life.get(player)
        val entries = m.specs.values.filter { it.shop == shop }.map { s ->
            ShopEntry(
                id = s.id,
                category = s.category,
                sub = s.sub,
                kind = s.kind,
                buy = m.buyQuote(s.id) ?: 0,
                sell = m.sellQuote(s.id) ?: 0,
                change = m.change(s.id).toFloat(),
                available = m.available(s.id),
                owned = countOf(player, s.id),
                history = m.state[s.id]!!.history.map { it.toFloat() },
            )
        }
        return ShopSnapshotPayload(shop, life.account.cash, life.account.bank, entries)
    }

    fun open(player: ServerPlayer, shop: String) {
        val s = snapshot(player, shop)
        if (s == null) {
            player.sendSystemMessage(Component.literal("시장이 아직 열리지 않았습니다"))
            return
        }
        PacketDistributor.sendToPlayer(player, s)
    }

    private fun countOf(player: ServerPlayer, id: String): Int =
        player.inventory.items.sumOf { if (MarketItems.matches(id, it)) it.count else 0 }

    private fun say(player: ServerPlayer, text: String, ok: Boolean) {
        player.displayClientMessage(Component.literal(text).withStyle(if (ok) ChatFormatting.GREEN else ChatFormatting.RED), true)
    }

    /** A trade request from the UI. Always answers with a fresh snapshot of the same shop. */
    fun trade(player: ServerPlayer, shop: String, id: String, qty: Int, buy: Boolean) {
        val m = market
        // the item must belong to the shop the request names
        if (m != null && m.specs[id]?.shop == shop && qty in 1..Market.MAX_ORDER) {
            if (buy) doBuy(player, m, id, qty) else doSell(player, m, id, qty)
            data?.setDirty()
        }
        snapshot(player, shop)?.let { PacketDistributor.sendToPlayer(player, it) }
    }

    private fun doBuy(player: ServerPlayer, m: Market, id: String, qty: Int) {
        val maxStack = MarketItems.stackOf(id).maxStackSize
        if (maxStack <= 0) return
        val cost = m.buyCost(id, qty)
        if (cost == null) return say(player, "재고가 부족합니다", false)
        val life = Life.get(player)
        val paid = life.account.charge(cost)
        if (paid == null) return say(player, "돈이 부족합니다 (\$$cost 필요)", false)
        m.buy(id, qty)
        Life.set(player, life.copy(account = paid))
        var left = qty
        while (left > 0) {
            val n = minOf(left, maxStack)
            val stack = MarketItems.stackOf(id, n)
            if (!player.inventory.add(stack)) player.drop(stack, false)
            left -= n
        }
        say(player, "구매 ${qty}개 -\$$cost", true)
    }

    private fun doSell(player: ServerPlayer, m: Market, id: String, qty: Int) {
        if (countOf(player, id) < qty) return say(player, "보유 수량이 부족합니다", false)
        val value = m.sellValue(id, qty)
        if (value == null) return say(player, "시장이 더 받지 못합니다", false)
        var left = qty
        for (stack in player.inventory.items) {
            if (left == 0) break
            if (!MarketItems.matches(id, stack)) continue
            val n = minOf(left, stack.count)
            stack.shrink(n)
            left -= n
        }
        m.sell(id, qty)
        Life.update(player) { it.copy(account = it.account.withCash(value) ?: it.account) }
        say(player, "판매 ${qty}개 +\$$value", true)
    }
}
