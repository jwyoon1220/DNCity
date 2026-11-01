package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.rear.BannerPostRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.ExtendableRearAttachment;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.util.TriCons;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.HashMap;
import java.util.Map;

public enum CommonPackets {;
    public static final Map<ResourceLocation, TriCons<MinecraftServer, ServerPlayer, FriendlyByteBuf>> SERVERBOUND_HANDLERS = new HashMap<>();

    public static void registerReceiver(ResourceLocation rl, TriCons<MinecraftServer, ServerPlayer, FriendlyByteBuf> run) {
        SERVERBOUND_HANDLERS.put(rl, run);
    }

    public static void sendClientboundAutomobileSyncPacket(AutomobileEntity entity, ServerPlayer player) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        entity.writeSyncStateData(buf);
        Platform.get().serverSendPacket(player, Automobility.rl("sync_automobile_data"), buf);
    }

    public static void sendSyncAutomobileAttachmentsPacket(AutomobileEntity entity, ServerPlayer player) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        buf.writeUtf(entity.getRearAttachmentType().id().toString());
        buf.writeUtf(entity.getFrontAttachmentType().id().toString());
        Platform.get().serverSendPacket(player, Automobility.rl("sync_automobile_attachments"), buf);
    }

    public static void sendBannerPostAttachmentUpdatePacket(AutomobileEntity entity, DyeColor baseColor, BannerPatternLayers layers, ServerPlayer player) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.level().registryAccess());

        if (entity.getRearAttachment() instanceof BannerPostRearAttachment) {
            buf.writeInt(entity.getId());
            if (baseColor != null && layers != null) {
                buf.writeBoolean(true);
                DyeColor.STREAM_CODEC.encode(buf, baseColor);
                BannerPatternLayers.STREAM_CODEC.encode(buf, layers);
            } else {
                buf.writeBoolean(false);
            }
            Platform.get().serverSendPacket(player, Automobility.rl("update_banner_post"), buf);
        }
    }

    public static void sendExtendableAttachmentUpdatePacket(AutomobileEntity entity, boolean extended, ServerPlayer player) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());

        if (entity.getRearAttachment() instanceof ExtendableRearAttachment) {
            buf.writeInt(entity.getId());
            buf.writeBoolean(extended);
            Platform.get().serverSendPacket(player, Automobility.rl("update_extendable_attachment"), buf);
        }
    }

    public static void init() {
        CommonPackets.registerReceiver(Automobility.rl("sync_automobile_data"), (server, player, buf) -> {
            var dup = new FriendlyByteBuf(buf.copy());
            int entityId = dup.readInt();
            server.execute(() -> {
                if (player.level().getEntity(entityId) instanceof AutomobileEntity automobile && automobile.isDriving(player)) {
                    automobile.readSyncStateData(dup);
                    automobile.markDirty();
                }
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("request_sync_automobile_components"), (server, player, buf) -> {
            int entityId = buf.readInt();
            server.execute(() -> {
                if (player.level().getEntity(entityId) instanceof AutomobileEntity automobile) {
                    sendSyncAutomobileAttachmentsPacket(automobile, player);

                    var fAtt = automobile.getFrontAttachment();
                    if (fAtt != null) fAtt.updatePacketRequested(player);

                    var rAtt = automobile.getRearAttachment();
                    if (rAtt != null) rAtt.updatePacketRequested(player);
                }
            });
        });
    }
}
