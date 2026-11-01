package com.atsuishio.superbwarfare.capability.player

import com.atsuishio.superbwarfare.Mod
import com.atsuishio.superbwarfare.config.server.AmmoConfig
import com.atsuishio.superbwarfare.data.gun.Ammo
import com.atsuishio.superbwarfare.init.ModAttachments
import com.atsuishio.superbwarfare.network.message.receive.CaliberAmmoSyncMessage
import com.atsuishio.superbwarfare.network.message.receive.PlayerVariablesSyncMessage
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.INBTSerializable
import net.neoforged.neoforge.event.entity.player.PlayerEvent.*
import net.neoforged.neoforge.network.PacketDistributor
import java.util.*
import java.util.function.Consumer

class PlayerVariable : INBTSerializable<CompoundTag> {
    private var old: PlayerVariable? = null

    @JvmField
    var ammo: MutableMap<Ammo, Int> = EnumMap(Ammo::class.java)

    /** TACZ 弹药口径（ResourceLocation）→ 数量的弹药池，供转换为使用 TACZ 弹药的枪械（[com.atsuishio.superbwarfare.data.gun.ammo_consumer_strategy.TaczAmmoStrategy]）使用 */
    @JvmField
    var caliberAmmo: MutableMap<ResourceLocation, Int> = HashMap()
    var activeThermalImaging: Boolean = false

    fun getCaliberAmmo(id: ResourceLocation): Int = caliberAmmo.getOrDefault(id, 0)

    fun setCaliberAmmo(id: ResourceLocation, count: Int): Boolean {
        var count = count
        if (count < 0) count = 0
        if (count > AmmoConfig.CALIBER_AMMO_LIMIT.get()) return false

        if (count == 0) {
            caliberAmmo.remove(id)
        } else {
            caliberAmmo[id] = count
        }
        return true
    }

    fun addCaliberAmmo(id: ResourceLocation, count: Int): Boolean {
        val newCount = (getCaliberAmmo(id).toLong() + count.toLong())
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return setCaliberAmmo(id, newCount)
    }

    fun sync(entity: Entity) {
        if (!entity.hasData(ModAttachments.PLAYER_VARIABLE)) return

        val newVariable = entity.getData(ModAttachments.PLAYER_VARIABLE)
        if (old != null && old == newVariable) return

        if (entity is ServerPlayer) {
            PacketDistributor.sendToPlayer(entity, PlayerVariablesSyncMessage(entity.id, compareAndUpdate()))

            val caliberDiff = compareAndUpdateCaliberAmmo()
            if (caliberDiff.isNotEmpty()) {
                PacketDistributor.sendToPlayer(entity, CaliberAmmoSyncMessage(entity.id, caliberDiff))
            }
        }
    }

    fun compareAndUpdateCaliberAmmo(): MutableMap<String, Int> {
        val map = hashMapOf<String, Int>()
        val old = (this.old ?: PlayerVariable())

        val ids = HashSet<ResourceLocation>(old.caliberAmmo.keys)
        ids.addAll(this.caliberAmmo.keys)

        for (id in ids) {
            val oldCount = old.caliberAmmo.getOrDefault(id, 0)
            val newCount = this.getCaliberAmmo(id)
            if (oldCount != newCount) {
                map[id.toString()] = newCount
            }
        }

        return map
    }

    fun forceUpdateCaliberAmmo(): MutableMap<String, Int> {
        val map = hashMapOf<String, Int>()
        for ((id, count) in caliberAmmo) {
            map[id.toString()] = count
        }
        return map
    }

    fun watch(): PlayerVariable {
        this.old = this.copy()
        return this
    }

    fun forceUpdate(): MutableMap<Byte, Int> {
        val map = hashMapOf<Byte, Int>()

        for (type in Ammo.entries) {
            map[type.ordinal.toByte()] = type.get(this)
        }

        map[(-1).toByte()] = if (this.activeThermalImaging) 1 else 0

        return map
    }

    fun compareAndUpdate(): MutableMap<Byte, Int> {
        val map = hashMapOf<Byte, Int>()
        val old = (if (this.old == null) PlayerVariable() else this.old)!!

        for (type in Ammo.entries) {
            val oldCount = old.ammo.getOrDefault(type, 0)
            val newCount = type.get(this)

            if (oldCount != newCount) {
                map[type.ordinal.toByte()] = newCount
            }
        }

        if (old.activeThermalImaging != this.activeThermalImaging) {
            map[(-1).toByte()] = if (this.activeThermalImaging) 1 else 0
        }

        return map
    }

    fun writeToNBT(): CompoundTag {
        val nbt = CompoundTag()

        for (type in Ammo.entries) {
            type.set(nbt, type.get(this))
        }

        val caliberNbt = CompoundTag()
        for ((id, count) in caliberAmmo) {
            caliberNbt.putInt(id.toString(), count)
        }
        nbt.put("CaliberAmmo", caliberNbt)

        nbt.putBoolean("ActiveThermalImaging", activeThermalImaging)

        return nbt
    }

    fun readFromNBT(tag: CompoundTag) {
        for (type in Ammo.entries) {
            type.set(this, type.get(tag))
        }

        caliberAmmo.clear()
        val caliberNbt = tag.getCompound("CaliberAmmo")
        for (key in caliberNbt.allKeys) {
            val id = ResourceLocation.tryParse(key) ?: continue
            caliberAmmo[id] = caliberNbt.getInt(key)
        }

        activeThermalImaging = tag.getBoolean("ActiveThermalImaging")
    }

    fun copy(): PlayerVariable {
        val clone = PlayerVariable()

        for (type in Ammo.entries) {
            type.set(clone, type.get(this))
        }

        clone.caliberAmmo = HashMap(this.caliberAmmo)
        clone.activeThermalImaging = this.activeThermalImaging

        return clone
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PlayerVariable) return false

        for (type in Ammo.entries) {
            if (type.get(this) != type.get(other)) return false
        }

        if (caliberAmmo != other.caliberAmmo) return false

        return activeThermalImaging == other.activeThermalImaging
    }

    override fun serializeNBT(provider: HolderLookup.Provider): CompoundTag {
        return writeToNBT()
    }

    override fun deserializeNBT(provider: HolderLookup.Provider, nbt: CompoundTag) {
        readFromNBT(nbt)
    }

    @EventBusSubscriber(modid = Mod.MODID)
    companion object {
        @JvmStatic
        fun modify(player: Player, consumer: Consumer<PlayerVariable>) {
            val cap = player.getData(ModAttachments.PLAYER_VARIABLE).watch()
            consumer.accept(cap)
            cap.sync(player)
        }

        @JvmStatic
        fun getOrDefault(entity: Entity): PlayerVariable {
            return entity.getData(ModAttachments.PLAYER_VARIABLE)
        }

        @SubscribeEvent
        fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
            val player = event.entity
            if (player !is ServerPlayer) return

            val variable = getOrDefault(player)
            PacketDistributor.sendToPlayer(
                player,
                PlayerVariablesSyncMessage(player.id, variable.compareAndUpdate())
            )
            val caliberForceUpdate = variable.forceUpdateCaliberAmmo()
            if (caliberForceUpdate.isNotEmpty()) {
                PacketDistributor.sendToPlayer(player, CaliberAmmoSyncMessage(player.id, caliberForceUpdate))
            }
        }

        @SubscribeEvent
        fun onPlayerRespawn(event: PlayerRespawnEvent) {
            val player = event.entity
            if (player !is ServerPlayer) return

            PacketDistributor.sendToPlayer(
                player,
                PlayerVariablesSyncMessage(player.id, getOrDefault(player).compareAndUpdate())
            )
        }

        @SubscribeEvent
        fun onPlayerChangeDimension(event: PlayerChangedDimensionEvent) {
            val player = event.entity
            if (player !is ServerPlayer) return

            val variable = getOrDefault(player)
            PacketDistributor.sendToPlayer(
                player,
                PlayerVariablesSyncMessage(player.id, variable.forceUpdate())
            )
            val caliberForceUpdate = variable.forceUpdateCaliberAmmo()
            if (caliberForceUpdate.isNotEmpty()) {
                PacketDistributor.sendToPlayer(player, CaliberAmmoSyncMessage(player.id, caliberForceUpdate))
            }
        }

        @SubscribeEvent
        fun clonePlayer(event: Clone) {
            event.original.revive()
            val original = event.original.getData(ModAttachments.PLAYER_VARIABLE)
            if (event.entity.level().isClientSide()) return
            event.entity.setData(ModAttachments.PLAYER_VARIABLE, original.copy())
        }
    }

    override fun hashCode(): Int {
        var result = activeThermalImaging.hashCode()
        result = 31 * result + (old?.hashCode() ?: 0)
        result = 31 * result + ammo.hashCode()
        result = 31 * result + caliberAmmo.hashCode()
        return result
    }
}
