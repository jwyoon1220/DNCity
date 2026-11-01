package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.rear.BannerPostRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.ExtendableRearAttachment;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public enum ClientPackets {;
    public static final Map<ResourceLocation, BiConsumer<Minecraft, FriendlyByteBuf>> CLIENTBOUND_HANDLERS = new HashMap<>();

    public static void registerReceiver(ResourceLocation rl, BiConsumer<Minecraft, FriendlyByteBuf> run) {
        CLIENTBOUND_HANDLERS.put(rl, run);
    }

    public static void sendServerboundAutomobileSyncPacket(AutomobileEntity entity) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        entity.writeSyncStateData(buf);
        Platform.get().clientSendPacket(Automobility.rl("sync_automobile_data"), buf);
    }

    public static void requestSyncAutomobileComponentsPacket(AutomobileEntity entity) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        Platform.get().clientSendPacket(Automobility.rl("request_sync_automobile_components"), buf);
    }

    public static void initClient() {
        ClientPackets.registerReceiver(Automobility.rl("sync_automobile_data"), (client, buf) -> {
            var dup = new FriendlyByteBuf(buf.copy());
            int entityId = dup.readInt();
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile) {
                    automobile.readSyncStateData(dup);
                }
            });
        });
        ClientPackets.registerReceiver(Automobility.rl("sync_automobile_attachments"), (client, buf) -> {
            int entityId = buf.readInt();
            var rearAtt = RearAttachmentType.REGISTRY.getOrDefault(ResourceLocation.tryParse(buf.readUtf()));
            var frontAtt = FrontAttachmentType.REGISTRY.getOrDefault(ResourceLocation.tryParse(buf.readUtf()));
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile) {
                    automobile.setRearAttachment(rearAtt);
                    automobile.setFrontAttachment(frontAtt);
                }
            });
        });
        ClientPackets.registerReceiver(Automobility.rl("update_banner_post"), (client, buf) -> {
            var rbuf = new RegistryFriendlyByteBuf(buf, client.level.registryAccess());

            int entityId = buf.readInt();
            final DyeColor baseColor;
            final BannerPatternLayers layers;
            if (buf.readBoolean()) {
                baseColor = DyeColor.STREAM_CODEC.decode(buf);
                layers = BannerPatternLayers.STREAM_CODEC.decode(rbuf);
            } else {
                baseColor = null;
                layers = null;
            }
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile &&
                        automobile.getRearAttachment() instanceof BannerPostRearAttachment bannerPost) {
                    bannerPost.setBanner(baseColor, layers);
                }
            });
        });
        ClientPackets.registerReceiver(Automobility.rl("update_extendable_attachment"), (client, buf) -> {
            int entityId = buf.readInt();
            boolean extended = buf.readBoolean();
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile &&
                        automobile.getRearAttachment() instanceof ExtendableRearAttachment att) {
                    att.setExtended(extended);
                }
            });
        });
    }
}
