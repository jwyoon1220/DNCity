// AGENT-DONE(claude): life-sim
package io.github.jwyoon1220.dncity.life

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.github.jwyoon1220.dncity.Dncity
import io.github.jwyoon1220.dncity.life.core.Account
import io.github.jwyoon1220.dncity.life.core.Economy
import io.github.jwyoon1220.dncity.life.core.JobState
import io.github.jwyoon1220.dncity.life.core.Wanted
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/** Everything the life sim remembers about one player, saved with the player and kept through death/respawn. */
data class LifeData(
    val account: Account = Account(cash = Economy.STARTING_CASH),
    val wanted: Wanted = Wanted(),
    val job: JobState = JobState(),
) {
    companion object {
        val CODEC: Codec<LifeData> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.LONG.optionalFieldOf("cash", Economy.STARTING_CASH).forGetter { it.account.cash },
                Codec.LONG.optionalFieldOf("bank", 0L).forGetter { it.account.bank },
                Codec.DOUBLE.optionalFieldOf("heat", 0.0).forGetter { it.wanted.heat },
                Codec.INT.optionalFieldOf("since_crime", 0).forGetter { it.wanted.ticksSinceCrime },
                Codec.STRING.optionalFieldOf("job", "").forGetter { it.job.jobId },
                Codec.INT.optionalFieldOf("rank", 0).forGetter { it.job.rank },
                Codec.INT.optionalFieldOf("shifts", 0).forGetter { it.job.shifts },
                Codec.INT.optionalFieldOf("shift_ticks", 0).forGetter { it.job.shiftTicksDone },
            ).apply(i) { cash, bank, heat, since, job, rank, shifts, shiftTicks ->
                LifeData(
                    Account(cash.coerceAtLeast(0), bank.coerceAtLeast(0)),
                    Wanted(heat.coerceAtLeast(0.0), since.coerceAtLeast(0)),
                    JobState(job, rank.coerceAtLeast(0), shifts.coerceAtLeast(0), shiftTicks.coerceAtLeast(0)),
                )
            }
        }
    }
}

object LifeAttachments {
    val REGISTRY: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Dncity.ID)

    val LIFE: DeferredHolder<AttachmentType<*>, AttachmentType<LifeData>> =
        REGISTRY.register<AttachmentType<LifeData>>(
            "life",
            Supplier { AttachmentType.builder(Supplier { LifeData() }).serialize(LifeData.CODEC).copyOnDeath().build() },
        )
}

/** Read/write access. Server-side only: the client never sees this data except through messages. */
object Life {
    fun get(player: ServerPlayer): LifeData = player.getData(LifeAttachments.LIFE)

    fun set(player: ServerPlayer, data: LifeData) {
        player.setData(LifeAttachments.LIFE, data)
    }

    inline fun update(player: ServerPlayer, f: (LifeData) -> LifeData) = set(player, f(get(player)))
}
