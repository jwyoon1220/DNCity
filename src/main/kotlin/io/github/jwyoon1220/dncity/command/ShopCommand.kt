// AGENT-DONE(claude): life-sim-market
package io.github.jwyoon1220.dncity.command

import io.github.jwyoon1220.dncity.life.market.MarketService
import net.minecraft.commands.Commands
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** `/shop` and `/ammoshop` open the market UIs (ModernUI, see client/shop/ShopFragment). */
object ShopCommand {
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("shop").executes { ctx ->
                MarketService.open(ctx.source.playerOrException, MarketService.SHOP_GENERAL)
                1
            },
        )
        event.dispatcher.register(
            Commands.literal("ammoshop").executes { ctx ->
                MarketService.open(ctx.source.playerOrException, MarketService.SHOP_AMMO)
                1
            },
        )
    }
}
