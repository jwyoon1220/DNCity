// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.life.market

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketCatalogTest {
    @Test
    fun `catalog lists unique items with only an initial price`() {
        val stream = javaClass.getResourceAsStream("/dncity/market_items.json")!!
        val items = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }.getAsJsonArray("items")
        val ids = items.map { it.asJsonObject["id"].asString }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids")
        assertTrue(ids.size > 100)
        for (e in items) {
            val o = e.asJsonObject
            assertTrue(o["price"].asDouble > 0, o["id"].asString)
            assertTrue(o["depth"].asDouble > 0, o["id"].asString)
            assertTrue(o["id"].asString.contains(':'))
        }
    }

    @Test
    fun `ammo shop is classified in three levels and kept out of the general shop`() {
        val stream = javaClass.getResourceAsStream("/dncity/market_items.json")!!
        val items = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }.getAsJsonArray("items").map { it.asJsonObject }
        val ammo = items.filter { it["shop"].asString == "ammo" }
        val general = items.filter { it["shop"].asString == "general" }
        assertEquals(setOf("미사일", "로켓·폭탄", "포탄", "소화기 탄약"), ammo.map { it["category"].asString }.toSet())
        for (a in ammo) {
            assertTrue(a["sub"].asString.isNotBlank(), "no sub: ${a["id"]}")
            assertTrue(a["kind"].asString.isNotBlank(), "no kind: ${a["id"]}")
        }
        assertTrue(general.none { it["id"].asString.contains("cartridge") || it["id"].asString.contains("shell") })
        val shells = ammo.filter { it["category"].asString == "포탄" }
        val subs = shells.map { it["sub"].asString }
        assertTrue(subs.any { it.contains("120mm") } && subs.any { it.contains("125mm") } && subs.any { it.contains("브래들리") })
        val kinds = shells.map { it["kind"].asString }.toSet()
        assertTrue("APFSDS" in kinds && "HEAT" in kinds && "HE" in kinds && "AP" in kinds)
    }
}
