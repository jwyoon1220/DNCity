// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.client.shop

import icyllis.modernui.mc.MuiModApi
import icyllis.modernui.mc.MuiScreen
import io.github.jwyoon1220.dncity.network.ShopSnapshotPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/** Client entry for the shop: a snapshot either refreshes the open [ShopFragment] or opens it. Game thread only. */
object ShopClient {
    fun handleSnapshot(snapshot: ShopSnapshotPayload) {
        val screen = Minecraft.getInstance().screen
        val open = (screen as? MuiScreen)?.fragment as? ShopFragment
        if (open != null && open.shop == snapshot.shop) {
            open.update(snapshot)
            return
        }
        // explicit `Screen` type: see PhoneController.open
        val ui: Screen = MuiModApi.get().createScreen(ShopFragment(snapshot))
        Minecraft.getInstance().setScreen(ui)
    }
}
