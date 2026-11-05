// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life

import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.jwyoon1220.dncity.life.core.Economy
import io.github.jwyoon1220.dncity.life.core.Jobs
import io.github.jwyoon1220.dncity.life.core.Pool
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** `/life`: wallet, bank, jobs and the wanted level. Admin subcommands need permission level 2. */
object LifeCommand {
    private fun fail(msg: String) = SimpleCommandExceptionType(Component.literal(msg)).create()

    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val d = event.dispatcher
        d.register(
            Commands.literal("life")
                .then(Commands.literal("status").executes { ctx -> status(ctx.source) })
                .then(
                    Commands.literal("deposit").then(
                        Commands.argument("amount", LongArgumentType.longArg(1)).executes { ctx ->
                            val p = ctx.source.playerOrException
                            val amount = LongArgumentType.getLong(ctx, "amount")
                            val data = Life.get(p)
                            val acc = data.account.deposit(amount) ?: throw fail("현금이 부족합니다")
                            Life.set(p, data.copy(account = acc))
                            reply(ctx.source, "입금 \$$amount  (현금 \$${acc.cash} · 통장 \$${acc.bank})")
                        },
                    ),
                )
                .then(
                    Commands.literal("withdraw").then(
                        Commands.argument("amount", LongArgumentType.longArg(1)).executes { ctx ->
                            val p = ctx.source.playerOrException
                            val amount = LongArgumentType.getLong(ctx, "amount")
                            val data = Life.get(p)
                            val acc = data.account.withdraw(amount) ?: throw fail("통장 잔액이 부족합니다")
                            Life.set(p, data.copy(account = acc))
                            reply(ctx.source, "출금 \$$amount  (현금 \$${acc.cash} · 통장 \$${acc.bank})")
                        },
                    ),
                )
                .then(
                    Commands.literal("pay").then(
                        Commands.argument("target", EntityArgument.player()).then(
                            Commands.argument("amount", LongArgumentType.longArg(1)).executes { ctx ->
                                val from = ctx.source.playerOrException
                                val to = EntityArgument.getPlayer(ctx, "target")
                                if (from === to) throw fail("자기 자신에게는 송금할 수 없습니다")
                                val amount = LongArgumentType.getLong(ctx, "amount")
                                val fd = Life.get(from)
                                val td = Life.get(to)
                                val (f, t) = Economy.transfer(fd.account, td.account, amount) ?: throw fail("현금이 부족합니다")
                                Life.set(from, fd.copy(account = f))
                                Life.set(to, td.copy(account = t))
                                to.sendSystemMessage(Component.literal("${from.gameProfile.name}님이 \$$amount 을(를) 보냈습니다"))
                                reply(ctx.source, "${to.gameProfile.name}님에게 \$$amount 송금")
                            },
                        ),
                    ),
                )
                .then(
                    Commands.literal("job")
                        .then(Commands.literal("list").executes { ctx ->
                            Jobs.ALL.forEach { j ->
                                val first = j.ranks.first()
                                val last = j.ranks.last()
                                ctx.source.sendSuccess({
                                    Component.literal("${j.id}: ${j.ko}${if (j.military) " [군]" else ""}  \$${first.wagePerShift}~\$${last.wagePerShift}/교대")
                                }, false)
                            }
                            1
                        })
                        .then(
                            Commands.literal("join").then(
                                Commands.argument("id", StringArgumentType.word())
                                    .suggests { _, b -> net.minecraft.commands.SharedSuggestionProvider.suggest(Jobs.ALL.map { it.id }, b) }
                                    .executes { ctx ->
                                        val p = ctx.source.playerOrException
                                        val id = StringArgumentType.getString(ctx, "id")
                                        val job = Jobs.find(id) ?: throw fail("없는 직업입니다: $id")
                                        val data = Life.get(p)
                                        val state = Jobs.join(data.job, id) ?: throw fail("이미 직업이 있습니다. 먼저 /life job quit")
                                        Life.set(p, data.copy(job = state))
                                        reply(ctx.source, "${job.ko} 취업: ${job.ranks[0].ko}")
                                    },
                            ),
                        )
                        .then(Commands.literal("quit").executes { ctx ->
                            val p = ctx.source.playerOrException
                            val data = Life.get(p)
                            if (!data.job.employed) throw fail("직업이 없습니다")
                            Life.set(p, data.copy(job = Jobs.quit(data.job)))
                            reply(ctx.source, "퇴직했습니다")
                        }),
                )
                .then(Commands.literal("surrender").executes { ctx ->
                    val p = ctx.source.playerOrException
                    val data = Life.get(p)
                    if (data.wanted.stars == 0) throw fail("수배 중이 아닙니다")
                    val bail = data.wanted.bail()
                    val acc = data.account.charge(bail) ?: throw fail("보석금 \$$bail 이 필요합니다 (현금+통장 부족)")
                    Life.set(p, data.copy(account = acc, wanted = data.wanted.copy(heat = 0.0, ticksSinceCrime = 0)))
                    reply(ctx.source, "자수했습니다. 보석금 \$$bail 납부")
                })
                .then(
                    Commands.literal("admin").requires { it.hasPermission(2) }
                        .then(
                            Commands.literal("give").then(
                                Commands.argument("target", EntityArgument.player()).then(
                                    Commands.argument("amount", LongArgumentType.longArg(1)).executes { ctx ->
                                        val to = EntityArgument.getPlayer(ctx, "target")
                                        val amount = LongArgumentType.getLong(ctx, "amount")
                                        Life.update(to) { it.copy(account = it.account.credit(amount)) }
                                        reply(ctx.source, "${to.gameProfile.name}님의 통장에 \$$amount 지급")
                                    },
                                ),
                            ),
                        )
                        // /life admin money <set|add|take> <player> <cash|bank> <amount>
                        .then(
                            Commands.literal("money")
                                .then(moneyOp("set") { a, pool, v -> a.with(pool, v) })
                                .then(moneyOp("add") { a, pool, v -> a.adjust(pool, v) })
                                .then(moneyOp("take") { a, pool, v -> a.adjust(pool, -v) }),
                        )
                        .then(
                            Commands.literal("clearwanted").then(
                                Commands.argument("target", EntityArgument.player()).executes { ctx ->
                                    val to = EntityArgument.getPlayer(ctx, "target")
                                    Life.update(to) { it.copy(wanted = it.wanted.copy(heat = 0.0, ticksSinceCrime = 0)) }
                                    reply(ctx.source, "${to.gameProfile.name}님의 수배를 해제")
                                },
                            ),
                        ),
                ),
        )
    }

    private fun moneyOp(
        name: String,
        op: (io.github.jwyoon1220.dncity.life.core.Account, Pool, Long) -> io.github.jwyoon1220.dncity.life.core.Account,
    ) = Commands.literal(name).then(
        Commands.argument("target", EntityArgument.player()).then(
            Commands.argument("pool", StringArgumentType.word())
                .suggests { _, b -> net.minecraft.commands.SharedSuggestionProvider.suggest(listOf("cash", "bank"), b) }
                .then(
                    Commands.argument("amount", LongArgumentType.longArg(0)).executes { ctx ->
                        val to = EntityArgument.getPlayer(ctx, "target")
                        val pool = when (StringArgumentType.getString(ctx, "pool").lowercase()) {
                            "cash" -> Pool.CASH
                            "bank" -> Pool.BANK
                            else -> throw fail("cash 또는 bank 를 입력하세요")
                        }
                        val amount = LongArgumentType.getLong(ctx, "amount")
                        val data = Life.get(to)
                        val next = op(data.account, pool, amount)
                        Life.set(to, data.copy(account = next))
                        reply(ctx.source, "${to.gameProfile.name}: 현금 \$${next.cash}  통장 \$${next.bank}")
                    },
                ),
        ),
    )

    private fun reply(source: CommandSourceStack, text: String): Int {
        source.sendSuccess({ Component.literal(text) }, false)
        return 1
    }

    private fun status(source: CommandSourceStack): Int {
        val p = source.playerOrException
        val d = Life.get(p)
        val job = Jobs.find(d.job.jobId)
        val jobText = if (job == null) "무직" else "${job.ko} · ${job.ranks[d.job.rank.coerceIn(0, job.ranks.lastIndex)].ko}"
        reply(source, "현금 \$${d.account.cash}  통장 \$${d.account.bank}")
        reply(source, "직업: $jobText")
        reply(source, "수배: " + if (d.wanted.stars == 0) "없음" else "★".repeat(d.wanted.stars) + "☆".repeat(5 - d.wanted.stars))
        return 1
    }
}
