package io.github.foundationgames.automobility.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import io.github.foundationgames.automobility.controller.AutomobileController;
import io.github.foundationgames.automobility.util.HexCons;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Platform {
    static void init(Platform instance) {
        GlobalPlatformInstance.INSTANCE = instance;
    }

    static Platform get() {
        if (GlobalPlatformInstance.INSTANCE == null) {
            throw new RuntimeException("Automobility's load order was disrupted!");
        }

        return GlobalPlatformInstance.INSTANCE;
    }

    CreativeModeTab creativeTab(ResourceLocation rl, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator displayItemsGenerator);

    void builtinItemRenderer(Item item, HexCons<ItemStack, ItemDisplayContext, PoseStack, MultiBufferSource, Integer, Integer> renderer);

    void itemModelPredicate(Item item, ResourceLocation id, ItemPropertyFunction predicate);

    <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory);

    @Nullable BlockColor blockColor(BlockState state);

    <T extends BlockEntity> BlockEntityType<T> blockEntity(BiFunction<BlockPos, BlockState, T> factory, Block... blocks);

    <T extends BlockEntity> void blockEntityRenderer(BlockEntityType<T> type, Function<BlockEntityRendererProvider.Context, BlockEntityRenderer<T>> provider);

    void serverSendPacket(ServerPlayer player, ResourceLocation rl, FriendlyByteBuf buf);

    void clientSendPacket(ResourceLocation rl, FriendlyByteBuf buf);

    <T extends Entity> EntityType<T> entityType(MobCategory category, BiFunction<EntityType<?>, Level, T> factory, EntityDimensions size, int updateRate, int updateRange, boolean internal, String key);

    <T extends Entity> void entityRenderer(EntityType<T> entity, Function<EntityRendererProvider.Context, EntityRenderer<T>> factory);

    void registerDataSerializer(ResourceLocation id, EntityDataSerializer<?> serializer);

    void registerClientCommand(BiConsumer<CommandDispatcher<SharedSuggestionProvider>, CommandBuildContext> callback);

    SimpleParticleType simpleParticleType(boolean z);

    AutomobileController controller();

    Path getGameDir();
}
