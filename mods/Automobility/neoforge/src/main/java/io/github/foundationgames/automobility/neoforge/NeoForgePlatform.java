package io.github.foundationgames.automobility.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import io.github.foundationgames.automobility.controller.AutomobileController;
import io.github.foundationgames.automobility.neoforge.client.BEWLRs;
import io.github.foundationgames.automobility.neoforge.mixin.BlockColorsAccess;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.HexCons;
import io.github.foundationgames.automobility.util.network.AutomobilityPacketPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class NeoForgePlatform implements Platform {
    public static final NeoForgePlatform INSTANCE = new NeoForgePlatform();

    public final Map<ResourceLocation, EntityDataSerializer<?>> dataSerializers = new HashMap<>();

    public static void init() {
        Platform.init(INSTANCE);
    }

    @Override
    public CreativeModeTab creativeTab(ResourceLocation rl, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator displayItemsGenerator) {
        return CreativeModeTab.builder()
                .icon(icon)
                .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                .title(Component.translatable("itemGroup." + rl.getNamespace() + "." + rl.getPath()))
                .displayItems(displayItemsGenerator)
                .build();
    }

    @Override
    public void builtinItemRenderer(Item item, HexCons<ItemStack, ItemDisplayContext, PoseStack, MultiBufferSource, Integer, Integer> renderer) {
        BEWLRs.add(item, renderer);
    }

    @Override
    public void itemModelPredicate(Item item, ResourceLocation id, ItemPropertyFunction predicate) {
        ItemProperties.register(item, id, predicate);
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory) {
        return new MenuType<>(factory::apply, FeatureFlags.DEFAULT_FLAGS);
    }

    @Override
    public @Nullable BlockColor blockColor(BlockState state) {
        return ((BlockColorsAccess)Minecraft.getInstance().getBlockColors()).automobility$getForgeColorMap().get(state.getBlock());
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntity(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }

    @Override
    public <T extends BlockEntity> void blockEntityRenderer(BlockEntityType<T> type, Function<BlockEntityRendererProvider.Context, BlockEntityRenderer<T>> provider) {
        BlockEntityRenderers.register(type, provider::apply);
    }

    @Override
    public void serverSendPacket(ServerPlayer player, ResourceLocation rl, FriendlyByteBuf buf) {
        PacketDistributor.sendToPlayer(player, new AutomobilityPacketPayload(rl, AUtils.arrayOf(buf)));
    }

    @Override
    public void clientSendPacket(ResourceLocation rl, FriendlyByteBuf buf) {
        PacketDistributor.sendToServer(new AutomobilityPacketPayload(rl, AUtils.arrayOf(buf)));
    }

    @Override
    public <T extends Entity> EntityType<T> entityType(MobCategory category, BiFunction<EntityType<?>, Level, T> factory, EntityDimensions size, int updateRate, int updateRange, boolean internal, String key) {
        var builder = EntityType.Builder.of(factory::apply, category).sized(size.width(), size.height()).eyeHeight(size.eyeHeight()).updateInterval(updateRate).clientTrackingRange(updateRange);
        if (internal) {
            builder.noSave().noSummon();
        }
        return builder.build(key);
    }

    @Override
    public <T extends Entity> void entityRenderer(EntityType<T> entity, Function<EntityRendererProvider.Context, EntityRenderer<T>> factory) {
        EntityRenderers.register(entity, factory::apply);
    }

    @Override
    public void registerDataSerializer(ResourceLocation id, EntityDataSerializer<?> serializer) {
        dataSerializers.put(id, serializer);
    }

    @Override
    public void registerClientCommand(BiConsumer<CommandDispatcher<SharedSuggestionProvider>, CommandBuildContext> callback) {
        NeoForge.EVENT_BUS.<RegisterClientCommandsEvent>addListener(evt ->
                callback.accept((CommandDispatcher) evt.getDispatcher(), evt.getBuildContext()));
    }

    @Override
    public SimpleParticleType simpleParticleType(boolean z) {
        return new SimpleParticleType(z);
    }

    @Override
    public AutomobileController controller() {
        return AutomobileController.INCOMPATIBLE;
    }

    @Override
    public Path getGameDir() {
        return FMLLoader.getGamePath();
    }
}
