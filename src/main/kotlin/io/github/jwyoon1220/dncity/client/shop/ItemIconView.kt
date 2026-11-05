// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.client.shop

import icyllis.modernui.core.Context
import icyllis.modernui.mc.MinecraftSurfaceView
import net.minecraft.client.gui.GuiGraphics
import io.github.jwyoon1220.dncity.life.market.MarketItems
import net.minecraft.world.item.ItemStack

/**
 * An item icon inside a ModernUI layout. ModernUI's own `ContainerDrawHelper.drawItem` is disabled in this version, so this
 * hands the view's rectangle to Minecraft: [MinecraftSurfaceView] calls the renderer on the render thread with a
 * [GuiGraphics] whose (0,0) is the view's top-left, in GUI-scaled units, and the vanilla item renderer draws the stack
 * (16 GUI units wide, scaled here to fill the view).
 */
class ItemIconView(context: Context, itemId: String) : MinecraftSurfaceView(context) {
    @Volatile
    private var stack: ItemStack = stackOf(itemId)

    @Volatile
    private var surfaceWidth = 0

    init {
        setRenderer(object : Renderer {
            override fun onSurfaceChanged(width: Int, height: Int) {
                surfaceWidth = width
            }

            override fun onDraw(gr: GuiGraphics, mouseX: Int, mouseY: Int, deltaTick: Float, guiScale: Double, alpha: Float) {
                val s = stack
                if (s.isEmpty) return
                // the view is surfaceWidth pixels wide = surfaceWidth / guiScale GUI units; the item is 16 units
                val scale = if (surfaceWidth > 0) (surfaceWidth / guiScale / 16.0).toFloat() else 1f
                gr.pose().pushPose()
                gr.pose().scale(scale, scale, 1f)
                gr.renderItem(s, 0, 0)
                gr.pose().popPose()
            }
        })
    }

    fun setItem(itemId: String) {
        stack = stackOf(itemId)
    }

    private companion object {
        fun stackOf(key: String): ItemStack = MarketItems.stackOf(key)
    }
}
