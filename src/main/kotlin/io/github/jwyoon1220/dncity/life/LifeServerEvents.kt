// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life

import io.github.jwyoon1220.dncity.life.core.Crime
import io.github.jwyoon1220.dncity.life.core.Economy
import io.github.jwyoon1220.dncity.life.core.Jobs
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.entity.npc.AbstractVillager
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * GTA-style life loop, server side: payroll, wanted level, police response, death penalty, bounty.
 *
 * "Police" is any iron golem (the vanilla village guard) plus any online player whose job is a law job. Civilians are
 * villagers and wandering traders. Every number is a DESIGN value in `life/core`.
 */
object LifeServerEvents {
    /** Everything here runs once a second. */
    private const val STEP = 20
    private const val WITNESS_RADIUS = 24.0
    private const val POLICE_AGGRO_RADIUS = 32.0
    private const val ASSAULT_COOLDOWN_TICKS = 100L
    private const val BOUNTY_PER_STAR = 500L

    private val lastAssault = ConcurrentHashMap<UUID, Long>()

    fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        if (server.tickCount % STEP != 0) return
        for (player in server.playerList.players) {
            if (!player.isAlive || player.isSpectator) continue
            payroll(player)
            wanted(player)
        }
    }

    private fun payroll(player: ServerPlayer) {
        val data = Life.get(player)
        if (!data.job.employed) return
        val step = Jobs.advance(data.job, STEP)
        val newData = data.copy(job = step.state, account = data.account.credit(step.wage))
        Life.set(player, newData)
        if (step.wage > 0) {
            val job = Jobs.find(step.state.jobId)!!
            info(player, "급여 +\$${step.wage}  (${job.ko})")
        }
        if (step.promoted) {
            val job = Jobs.find(step.state.jobId)!!
            player.sendSystemMessage(
                Component.literal("승진! ${job.ko} · ${job.ranks[step.state.rank].ko}").withStyle(ChatFormatting.GOLD),
            )
        }
    }

    private fun wanted(player: ServerPlayer) {
        val data = Life.get(player)
        if (data.wanted.heat <= 0.0) return
        val police = nearbyPolice(player, WITNESS_RADIUS)
        val seen = police.any { player.hasLineOfSight(it) }
        val before = data.wanted.stars
        val next = data.wanted.tick(STEP, witnessed = seen)
        if (next != data.wanted) Life.set(player, data.copy(wanted = next))
        if (next.stars != before) announceStars(player, next.stars)
        // two stars and up: village guards come for the player
        if (next.stars >= 2) {
            for (golem in player.level().getEntitiesOfClass(IronGolem::class.java, player.boundingBox.inflate(POLICE_AGGRO_RADIUS))) {
                if (golem.target == null) golem.target = player
            }
        }
    }

    private fun nearbyPolice(player: ServerPlayer, radius: Double): List<LivingEntity> {
        val box = player.boundingBox.inflate(radius)
        val golems = player.level().getEntitiesOfClass(IronGolem::class.java, box)
        val cops = player.level().getEntitiesOfClass(ServerPlayer::class.java, box) {
            it !== player && isLaw(it)
        }
        return golems + cops
    }

    private fun isLaw(p: ServerPlayer): Boolean = Jobs.find(Life.get(p).job.jobId)?.law == true

    /** Records [crime] against [player]. Soldiers are court-martialled for crimes against civilians. */
    fun commit(player: ServerPlayer, crime: Crime) {
        val data = Life.get(player)
        val before = data.wanted.stars
        val martial = Jobs.courtMartial(data.job, crime)
        var account = data.account
        var job = data.job
        if (martial != null) {
            job = martial.first
            account = account.charge(martial.second) ?: account.charge(account.cash + account.bank)!!
            player.sendSystemMessage(
                Component.literal("군사재판: 민간인 대상 범죄로 전역 처분. 벌금 \$${martial.second}").withStyle(ChatFormatting.RED),
            )
        }
        val wanted = data.wanted.commit(crime)
        Life.set(player, data.copy(account = account, job = job, wanted = wanted))
        if (wanted.stars != before) announceStars(player, wanted.stars)
        info(player, "범죄: ${crime.ko}")
    }

    fun onDeath(event: LivingDeathEvent) {
        val victim = event.entity
        val killerEntity: Entity? = event.source.entity
        val killer = killerEntity as? ServerPlayer

        if (victim is ServerPlayer) {
            val vData = Life.get(victim)
            val death = Economy.onDeath(vData.account)
            // the wanted level is wiped by dying (a "wasted" screen), the job stays
            Life.set(victim, vData.copy(account = death.after, wanted = vData.wanted.copy(heat = 0.0, ticksSinceCrime = 0)))
            if (killer != null && killer !== victim) {
                val lawful = isLaw(killer) && vData.wanted.stars > 0
                if (lawful) {
                    val bounty = BOUNTY_PER_STAR * vData.wanted.stars
                    Life.update(killer) { it.copy(account = it.account.credit(bounty)) }
                    killer.sendSystemMessage(Component.literal("현상금 +\$$bounty").withStyle(ChatFormatting.GOLD))
                } else {
                    Life.update(killer) { it.copy(account = it.account.copy(cash = it.account.cash + death.dropped)) }
                    commit(killer, if (isLaw(victim)) Crime.KILL_POLICE else Crime.KILL_PLAYER)
                }
                if (death.dropped > 0) victim.sendSystemMessage(Component.literal("현금 \$${death.dropped}을(를) 잃었습니다").withStyle(ChatFormatting.RED))
            }
            return
        }

        if (killer == null) return
        when (victim) {
            is IronGolem -> commit(killer, Crime.KILL_POLICE)
            is AbstractVillager -> commit(killer, Crime.KILL_CIVILIAN)
        }
    }

    /** Hitting a player or a civilian is assault, at most once per [ASSAULT_COOLDOWN_TICKS] per attacker. */
    fun onDamage(event: LivingDamageEvent.Post) {
        val attacker = event.source.entity as? ServerPlayer ?: return
        val victim = event.entity
        if (victim === attacker) return
        val crime = when (victim) {
            is ServerPlayer -> {
                // arresting a wanted player is the law's job
                if (isLaw(attacker) && Life.get(victim).wanted.stars > 0) return
                Crime.ASSAULT
            }
            is AbstractVillager -> Crime.ASSAULT
            is IronGolem -> Crime.ATTACK_POLICE
            else -> return
        }
        val now = attacker.server.tickCount.toLong()
        val last = lastAssault[attacker.uuid]
        if (last != null && now - last < ASSAULT_COOLDOWN_TICKS) return
        lastAssault[attacker.uuid] = now
        commit(attacker, crime)
        // the victim's guards react right away
        if (victim is Mob && victim !is IronGolem) {
            attacker.level().getEntitiesOfClass(IronGolem::class.java, attacker.boundingBox.inflate(POLICE_AGGRO_RADIUS))
                .forEach { if (it.target == null) it.target = attacker }
        }
    }

    fun onRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        if (event.isEndConquered) return
        val player = event.entity as? ServerPlayer ?: return
        val data = Life.get(player)
        val (account, paid) = Economy.hospitalBill(data.account)
        if (paid > 0) {
            Life.set(player, data.copy(account = account))
            player.sendSystemMessage(Component.literal("병원비 \$$paid 가 청구되었습니다").withStyle(ChatFormatting.YELLOW))
        }
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        lastAssault.remove(event.entity.uuid)
    }

    private fun announceStars(player: ServerPlayer, stars: Int) {
        val text = if (stars == 0) Component.literal("수배가 해제되었습니다").withStyle(ChatFormatting.GREEN)
        else Component.literal("수배 " + "★".repeat(stars) + "☆".repeat(5 - stars)).withStyle(ChatFormatting.RED)
        player.displayClientMessage(text, true)
    }

    fun info(player: ServerPlayer, text: String) {
        player.displayClientMessage(Component.literal(text), true)
    }
}
