// AGENT-DONE(claude): police-dispatch
package io.github.jwyoon1220.dncity.command

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.jwyoon1220.dncity.dispatch.DispatchService
import io.github.jwyoon1220.dncity.radio.RadioBand
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * Admin tools for the police dispatch (see [DispatchService]):
 * `/dispatch test [crime]` raises a call at the executor's position, `/dispatch freq <kHz>` sets the radio frequency the
 * dispatcher speaks on (`/dispatch freq` alone shows it).
 */
object DispatchCommand {
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("dispatch")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("test")
                        .executes { ctx -> test(ctx.source, "총기 난사") }
                        .then(
                            Commands.argument("crime", StringArgumentType.greedyString())
                                .executes { ctx -> test(ctx.source, StringArgumentType.getString(ctx, "crime")) },
                        ),
                )
                .then(
                    Commands.literal("freq")
                        .executes { ctx ->
                            ctx.source.sendSuccess({ Component.literal("디스패치 주파수: ${DispatchService.frequencyKhz} kHz") }, false)
                            1
                        }
                        .then(
                            Commands.argument("khz", DoubleArgumentType.doubleArg(1.0))
                                .executes { ctx ->
                                    val khz = DoubleArgumentType.getDouble(ctx, "khz")
                                    if (RadioBand.fromFrequencyKhz(khz) == null) {
                                        ctx.source.sendFailure(Component.literal("어느 대역에도 속하지 않는 주파수입니다"))
                                        0
                                    } else {
                                        DispatchService.frequencyKhz = khz
                                        ctx.source.sendSuccess({ Component.literal("디스패치 주파수: $khz kHz") }, true)
                                        1
                                    }
                                },
                        ),
                ),
        )
    }

    private fun test(source: net.minecraft.commands.CommandSourceStack, crime: String): Int {
        val player = source.playerOrException
        val pos = player.blockPosition()
        DispatchService.raise(
            source.server, crime, 3, player.gameProfile.name,
            player.level().dimension().location().toString(), pos.x, pos.y, pos.z,
        )
        return 1
    }
}
