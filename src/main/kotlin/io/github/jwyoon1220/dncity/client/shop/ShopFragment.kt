// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.client.shop

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Paint
import icyllis.modernui.graphics.drawable.ShapeDrawable
import icyllis.modernui.mc.ScreenCallback
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import icyllis.modernui.widget.HorizontalScrollView
import io.github.jwyoon1220.dncity.life.market.MarketItems
import io.github.jwyoon1220.dncity.network.ShopEntry
import io.github.jwyoon1220.dncity.network.ShopSnapshotPayload
import io.github.jwyoon1220.dncity.network.ShopTradePayload
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.abs

/**
 * The market UI, a ModernUI [Fragment]. Everything here runs on ModernUI's UI thread (updates arrive through [View.post]);
 * network sends hop to the game thread. Colours follow the Korean market convention: up = red, down = blue.
 *
 * Left: category tabs and the item list (name, price, change). Right: the selected item with its price chart, supply,
 * how many you hold, a quantity stepper and Buy / Sell. The server re-validates every trade and answers with a fresh
 * snapshot, so what the screen shows is always the server's.
 */
class ShopFragment(private var snapshot: ShopSnapshotPayload) : Fragment(), ScreenCallback {

    private lateinit var root: FrameLayout
    private lateinit var wallet: TextView
    private lateinit var tabs: LinearLayout
    private lateinit var title: TextView
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var detailIcon: ItemIconView
    private lateinit var detailName: TextView
    private lateinit var detailPrice: TextView
    private lateinit var detailInfo: TextView
    private lateinit var chart: ChartView
    private lateinit var qtyText: TextView
    private lateinit var buyButton: Button
    private lateinit var sellButton: Button

    private var category = ALL
    private var sub = ALL
    private var kind = ALL

    val shop: String get() = snapshot.shop
    private val ammoShop: Boolean get() = snapshot.shop == "ammo"
    private var selected: String? = null
    private var qty = 1

    override fun isPauseScreen(): Boolean = false

    /** Called from the game thread when a newer snapshot arrives. */
    fun update(next: ShopSnapshotPayload) {
        snapshot = next
        if (::root.isInitialized) root.post { refresh() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: DataSet?): View {
        val ctx = requireContext()
        root = FrameLayout(ctx)

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BG, 14f)
            setPadding(dp(18f), dp(14f), dp(18f), dp(16f))
        }
        root.addView(panel, FrameLayout.LayoutParams(dp(820f), dp(if (ammoShop) 540f else 480f)).apply { gravity = Gravity.CENTER })

        // header
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title = text(ctx, if (ammoShop) "탄약 상점" else "시장", 22f, TEXT, bold = true)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wallet = text(ctx, "", 14f, MUTED)
        header.addView(wallet)
        panel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        panel.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10f) })

        // left: tabs + list
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        tabs = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        left.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll = ScrollView(ctx)
        list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        left.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8f) })
        // item icons are drawn by Minecraft, outside ModernUI's clipping, so a row that is not fully inside the scroll
        // area must not show its icon (checked every frame; setVisibility only invalidates on a real change)
        scroll.viewTreeObserver.addOnPreDrawListener {
            clipIcons()
            true
        }
        body.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        // right: detail
        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 10f)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }
        detailName = text(ctx, "품목을 선택하세요", 17f, TEXT, bold = true)
        detailPrice = text(ctx, "", 14f, TEXT)
        detailInfo = text(ctx, "", 12f, MUTED)
        chart = ChartView(ctx)
        val title = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        detailIcon = ItemIconView(ctx, "minecraft:air")
        title.addView(detailIcon, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { rightMargin = dp(10f) })
        title.addView(detailName, LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(title)
        right.addView(detailPrice, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4f) })
        right.addView(chart, LinearLayout.LayoutParams(-1, dp(110f)).apply { topMargin = dp(10f) })
        right.addView(detailInfo, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8f) })

        val stepper = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for ((label, delta) in listOf("-8" to -8, "-1" to -1)) stepper.addView(small(ctx, label) { setQty(qty + delta) })
        qtyText = text(ctx, "1", 16f, TEXT, bold = true).apply { gravity = Gravity.CENTER }
        stepper.addView(qtyText, LinearLayout.LayoutParams(dp(52f), -2))
        for ((label, delta) in listOf("+1" to 1, "+8" to 8)) stepper.addView(small(ctx, label) { setQty(qty + delta) })
        right.addView(stepper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })

        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        buyButton = action(ctx, "구매", RED) { trade(true) }
        sellButton = action(ctx, "판매", BLUE) { trade(false) }
        actions.addView(buyButton, LinearLayout.LayoutParams(0, dp(40f), 1f).apply { rightMargin = dp(6f) })
        actions.addView(sellButton, LinearLayout.LayoutParams(0, dp(40f), 1f).apply { leftMargin = dp(6f) })
        right.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        body.addView(right, LinearLayout.LayoutParams(dp(290f), ViewGroup.LayoutParams.MATCH_PARENT).apply { leftMargin = dp(14f) })

        refresh()
        return root
    }

    // ------------------------------------------------------------------ state -> views

    private fun entryOf(id: String?) = snapshot.entries.firstOrNull { it.id == id }

    private fun refresh() {
        val ctx = requireContext()
        wallet.text = "현금 \$${snapshot.cash}   통장 \$${snapshot.bank}"

        // three filter levels: category -> sub (caliber / gun / vehicle) -> kind (round type); a blank level is not shown
        val cats = snapshot.entries.map { it.category }.distinct()
        // the ammo shop starts on its first category instead of "all" so the list is already a sensible size
        if (ammoShop && category == ALL && !categoryChosen) category = cats.firstOrNull() ?: ALL
        if (category != ALL && category !in cats) category = ALL
        val inCat = snapshot.entries.filter { category == ALL || it.category == category }
        val subs = inCat.map { it.sub }.filter { it.isNotBlank() }.distinct()
        if (sub != ALL && sub !in subs) sub = ALL
        val inSub = inCat.filter { sub == ALL || it.sub == sub }
        val kinds = inSub.map { it.kind }.filter { it.isNotBlank() }.distinct()
        if (kind != ALL && kind !in kinds) kind = ALL

        tabs.removeAllViews()
        tabs.addView(filterRow(ctx, if (ammoShop) "분류" else "", listOf(ALL) + cats, category) {
            category = it; categoryChosen = true; sub = ALL; kind = ALL; refresh()
        })
        if (subs.isNotEmpty()) tabs.addView(filterRow(ctx, "구경·용도", listOf(ALL) + subs, sub) { sub = it; kind = ALL; refresh() })
        if (kinds.isNotEmpty()) tabs.addView(filterRow(ctx, "탄종", listOf(ALL) + kinds, kind) { kind = it; refresh() })

        val shown = inSub.filter { kind == ALL || it.kind == kind }
        list.removeAllViews()
        for (e in shown) {
            list.addView(row(ctx, e), LinearLayout.LayoutParams(-1, dp(34f)).apply { bottomMargin = dp(3f) })
        }
        if (shown.none { it.id == selected }) selected = shown.firstOrNull()?.id
        showDetail()
    }

    private var categoryChosen = false

    /** One row of filter tabs; scrolls sideways when the names do not fit. */
    private fun filterRow(ctx: Context, caption: String, values: List<String>, current: String, onPick: (String) -> Unit): View {
        val h = HorizontalScrollView(ctx)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (caption.isNotEmpty()) {
            row.addView(text(ctx, caption, 11f, MUTED), LinearLayout.LayoutParams(dp(56f), -2))
        }
        for (v in values) {
            row.addView(
                small(ctx, categoryName(v), if (v == current) ACCENT else CARD) { onPick(v) },
                LinearLayout.LayoutParams(-2, dp(28f)).apply { rightMargin = dp(6f) },
            )
        }
        h.addView(row, ViewGroup.LayoutParams(-2, -2))
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(h, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5f) })
        }
    }

    private fun clipIcons() {
        val top = scroll.scrollY
        val bottom = top + scroll.height
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? LinearLayout ?: continue
            val icon = row.getChildAt(0) as? ItemIconView ?: continue
            val visible = row.top >= top && row.bottom <= bottom
            val want = if (visible) View.VISIBLE else View.INVISIBLE
            if (icon.visibility != want) icon.visibility = want
        }
    }

    private fun row(ctx: Context, e: ShopEntry): View {
        val sel = e.id == selected
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (sel) ROW_SELECTED else CARD, 8f)
            setPadding(dp(10f), 0, dp(10f), 0)
            setOnClickListener { selected = e.id; setQty(1); refresh() }
        }
        r.addView(ItemIconView(ctx, e.id), LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { rightMargin = dp(8f) })
        r.addView(text(ctx, nameOf(e.id), 13f, TEXT).apply { setSingleLine() }, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(text(ctx, "\$${e.buy}", 13f, TEXT), LinearLayout.LayoutParams(dp(70f), -2))
        r.addView(text(ctx, pct(e.change), 12f, trendColor(e.change)).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(58f), -2))
        return r
    }

    private fun showDetail() {
        val e = entryOf(selected)
        if (e == null) {
            detailName.text = "품목을 선택하세요"
            detailIcon.setItem("minecraft:air")
            detailPrice.text = ""
            detailInfo.text = ""
            chart.history = emptyList()
            buyButton.isEnabled = false
            sellButton.isEnabled = false
            return
        }
        detailName.text = nameOf(e.id)
        detailIcon.setItem(e.id)
        detailPrice.text = "구매 \$${e.buy}   판매 \$${e.sell}   ${pct(e.change)}"
        detailPrice.setTextColor(trendColor(e.change))
        detailInfo.text = "시장 재고 ${e.available}   보유 ${e.owned}"
        chart.history = e.history
        qty = qty.coerceIn(1, maxQty(e))
        qtyText.text = qty.toString()
        buyButton.isEnabled = e.available >= 1
        sellButton.isEnabled = e.owned >= 1
    }

    private fun maxQty(e: ShopEntry) = maxOf(1, minOf(64, maxOf(e.available, e.owned)))

    private fun setQty(v: Int) {
        val e = entryOf(selected) ?: return
        qty = v.coerceIn(1, maxQty(e))
        qtyText.text = qty.toString()
    }

    private fun trade(buy: Boolean) {
        val id = selected ?: return
        val q = qty
        Minecraft.getInstance().execute { PacketDistributor.sendToServer(ShopTradePayload(snapshot.shop, id, q, buy)) }
    }

    // ------------------------------------------------------------------ widgets

    private fun text(ctx: Context, s: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTextStyle(icyllis.modernui.text.Typeface.BOLD)
    }

    private fun small(ctx: Context, label: String, color: Int = CARD, onClick: () -> Unit) = Button(ctx).apply {
        text = label
        textSize = 12f
        setTextColor(TEXT)
        background = rounded(color, 8f)
        setPadding(dp(10f), 0, dp(10f), 0)
        setOnClickListener { onClick() }
    }

    private fun action(ctx: Context, label: String, color: Int, onClick: () -> Unit) = Button(ctx).apply {
        text = label
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        background = rounded(color, 10f)
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float) = ShapeDrawable().apply {
        setShape(ShapeDrawable.RECTANGLE)
        setCornerRadius(root.dp(radius).toFloat())
        setColor(color)
    }

    private fun dp(v: Float) = root.dp(v)

    // ------------------------------------------------------------------ helpers

    private fun nameOf(id: String): String {
        if (MarketItems.isTaczAmmo(id)) {
            // TACZ ammo with an ammo id: the name key is `<namespace>.ammo.<path>.name` (data/<ns>/index/ammo)
            val rl = ResourceLocation.tryParse(id.removePrefix(MarketItems.TACZ_PREFIX)) ?: return id
            return net.minecraft.network.chat.Component.translatable("${rl.namespace}.ammo.${rl.path}.name").string
        }
        val rl = ResourceLocation.tryParse(id) ?: return id
        return BuiltInRegistries.ITEM.getOptional(rl).map { it.description.string }.orElse(id)
    }

    private fun categoryName(c: String) = when (c) {
        ALL -> "전체"
        "ammo" -> "탄약"
        "food" -> "식량"
        "medical" -> "의료"
        "gear" -> "장비"
        else -> c
    }

    private fun pct(v: Float): String {
        val p = v * 100f
        return (if (p > 0.05f) "+" else "") + String.format("%.1f%%", if (abs(p) < 0.05f) 0f else p)
    }

    private fun trendColor(v: Float) = when {
        v > 0.0005f -> RED
        v < -0.0005f -> BLUE
        else -> MUTED
    }

    /** Line chart of the recent price history. */
    private class ChartView(ctx: Context) : View(ctx) {
        var history: List<Float> = emptyList()
            set(v) {
                field = v
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val bg = Paint.obtain()
            bg.setColor(0x22FFFFFF)
            canvas.drawRoundRect(0f, 0f, w, h, dp(6f).toFloat(), bg)
            bg.recycle()
            if (history.size < 2) return
            val lo = history.min()
            val hi = history.max()
            val span = (hi - lo).coerceAtLeast(hi * 0.01f).coerceAtLeast(0.0001f)
            val pad = dp(8f).toFloat()
            val p = Paint.obtain()
            p.setStyle(Paint.STROKE)
            p.setStrokeWidth(dp(2f).toFloat())
            p.setAntiAlias(true)
            p.setColor(if (history.last() >= history.first()) RED else BLUE)
            var px = 0f
            var py = 0f
            for ((i, v) in history.withIndex()) {
                val x = pad + (w - 2 * pad) * i / (history.size - 1)
                val y = h - pad - (h - 2 * pad) * (v - lo) / span
                if (i > 0) canvas.drawLine(px, py, x, y, p)
                px = x
                py = y
            }
            p.recycle()
        }
    }

    private companion object {
        const val ALL = "*"
        const val BG = 0xF2141821.toInt()
        const val CARD = 0xFF202636.toInt()
        const val ROW_SELECTED = 0xFF2F3D63.toInt()
        const val ACCENT = 0xFF5B8DEF.toInt()
        const val TEXT = 0xFFE6E8EE.toInt()
        const val MUTED = 0xFF8A90A0.toInt()
        const val RED = 0xFFE5484D.toInt()
        const val BLUE = 0xFF3E7BFA.toInt()
    }
}
