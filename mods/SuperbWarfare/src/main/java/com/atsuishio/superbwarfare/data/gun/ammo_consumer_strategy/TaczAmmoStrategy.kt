package com.atsuishio.superbwarfare.data.gun.ammo_consumer_strategy

import com.atsuishio.superbwarfare.Mod
import com.atsuishio.superbwarfare.capability.player.PlayerVariable
import com.atsuishio.superbwarfare.client.language.ClientLanguageGetter
import com.atsuishio.superbwarfare.compat.CompatHolder
import com.atsuishio.superbwarfare.data.gun.Ammo
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer
import com.atsuishio.superbwarfare.data.gun.GunData
import com.atsuishio.superbwarfare.tools.InventoryTool
import com.tacz.guns.api.item.builder.AmmoItemBuilder
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import kotlin.math.min

/**
 * TACZ 弹药口径策略 — ammo 字符串形如 "@tacz:556x45"、"@tacz:9mm"。
 *
 * 复用 [PlayerAmmoStrategy] 的“玩家弹药池优先，物品栏兜底”结构，只是池的 key 从固定的 [Ammo]
 * 枚举换成了任意的 TACZ 弹药口径 [ResourceLocation]（[PlayerVariable.caliberAmmo]），
 * 填充该池所用的物品也从 SBW 自己的弹药物品换成了 TACZ 的 `tacz:ammo`（按 AmmoId 区分口径）。
 *
 * match: 以 "@" 开头，且后续内容是一个合法 ResourceLocation，但不是 [Ammo] 的序列化名
 * （避免抢先匹配 "@HeavyAmmo" 这类 legacy 写法，那些交由 [PlayerAmmoStrategy] 处理）。
 */
object TaczAmmoStrategy : AmmoConsumeStrategy() {

    override val defaultType = AmmoConsumer.AmmoConsumeType.CALIBER_AMMO

    override fun match(ammo: String): Boolean {
        if (!CompatHolder.hasTacz()) return false
        if (!ammo.startsWith("@")) return false

        val id = ammo.substringAfter("@").trim()
        if (Ammo.getType(id) != null) return false

        return ResourceLocation.tryParse(id) != null
    }

    override fun init(consumer: AmmoConsumer, count: Int, matchedString: String) {
        val id = matchedString.substringAfter("@").trim()
        val caliberId = ResourceLocation.tryParse(id)
        if (caliberId == null) {
            Mod.LOGGER.warn("invalid TACZ ammo caliber: {}", id)
            consumer.type = AmmoConsumer.AmmoConsumeType.INVALID
            return
        }

        consumer.caliberId = caliberId
        consumer.stack = AmmoItemBuilder.create().setId(caliberId).setCount(1).build()
    }

    override fun consume(data: GunData, consumer: AmmoConsumer, shooter: Entity, count: Int): Int {
        var remaining = count
        var consumed = 0

        val caliberId = consumer.caliberId
        if (shooter is Player) {
            if (caliberId != null) {
                val current = PlayerVariable.getOrDefault(shooter).getCaliberAmmo(caliberId)
                consumed = min(current, remaining)
                remaining -= consumed
                if (consumed > 0) {
                    PlayerVariable.modify(shooter) { it.addCaliberAmmo(caliberId, -consumed) }
                }
            } else {
                Mod.LOGGER.warn("consume TACZ ammo failed: invalid caliber id")
            }
        }

        val handler = shooter.getCapability(Capabilities.ItemHandler.ENTITY)
        if (handler != null) {
            return consumed + consume(data, consumer, handler, remaining)
        } else {
            Mod.LOGGER.warn("consume TACZ ammo failed: invalid item handler for entity {}", shooter)
            return consumed
        }
    }

    override fun consume(data: GunData, consumer: AmmoConsumer, handler: IItemHandler, count: Int): Int {
        val consumed = InventoryTool.consumeCaliberAmmoItem(handler, consumer.caliberId, count)
        val rest = consumed - count
        data.virtualAmmo.add(rest)
        return count
    }

    override fun count(data: GunData, consumer: AmmoConsumer, entity: Entity?): Int {
        if (entity == null) return 0

        var poolCount = 0
        val caliberId = consumer.caliberId
        if (entity is Player && caliberId != null) {
            poolCount = PlayerVariable.getOrDefault(entity).getCaliberAmmo(caliberId)
        }
        return poolCount + count(data, consumer, entity.getCapability(Capabilities.ItemHandler.ENTITY))
    }

    override fun count(data: GunData, consumer: AmmoConsumer, handler: IItemHandler?): Int {
        if (handler == null) return 0
        return InventoryTool.countCaliberAmmoItem(handler, consumer.caliberId)
    }

    override fun withdraw(consumer: AmmoConsumer, ammoSupplier: Entity, count: Int): Int {
        val caliberId = consumer.caliberId ?: run {
            Mod.LOGGER.warn("withdraw TACZ ammo failed: invalid caliber id")
            return 0
        }

        if (ammoSupplier is Player) {
            var withdrawnToPool = 0
            PlayerVariable.modify(ammoSupplier) {
                val before = it.getCaliberAmmo(caliberId)
                it.addCaliberAmmo(caliberId, count)
                withdrawnToPool = it.getCaliberAmmo(caliberId) - before
            }

            val restItemCount = count - withdrawnToPool
            if (restItemCount > 0) {
                val handler = ammoSupplier.getCapability(Capabilities.ItemHandler.ENTITY)
                InventoryTool.withdrawCaliberAmmoItem(handler, caliberId, restItemCount)
            }
            return count
        }

        val itemHandler = ammoSupplier.getCapability(Capabilities.ItemHandler.ENTITY)
        if (itemHandler != null) {
            return withdraw(consumer, itemHandler, count)
        }

        Mod.LOGGER.warn("withdraw TACZ ammo failed: invalid item handler")
        return 0
    }

    override fun withdraw(consumer: AmmoConsumer, handler: IItemHandler, count: Int): Int {
        val caliberId = consumer.caliberId ?: return 0
        return InventoryTool.withdrawCaliberAmmoItem(handler, caliberId, count)
    }

    @OnlyIn(Dist.CLIENT)
    override fun getDisplayName(consumer: AmmoConsumer): String {
        val stack = consumer.stack
        if (stack.isEmpty) return super.getDisplayName(consumer)

        val contents = stack.hoverName.contents
        if (contents is TranslatableContents) {
            return ClientLanguageGetter.EN_US.getOrDefault(contents.key)
        }
        return ClientLanguageGetter.EN_US.getOrDefault(stack.descriptionId)
    }
}
