package net.caffeinemc.mods.lithium.fabric.mixin.entity.collisions.fluid;

import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.caffeinemc.mods.lithium.common.block.BlockCountingSection;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.block.TrackedBlockStatePredicate;
import net.caffeinemc.mods.lithium.common.entity.FluidCachingEntity;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements FluidCachingEntity {

    @Shadow
    private Level level;

    @Shadow
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Inject(
            method = "updateFluidHeightAndDoFluidPushing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid()Z"
            ),
            cancellable = true
    )
    public void tryShortcutFluidPushing(TagKey<Fluid> tag, double speed, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) int x1, @Local(ordinal = 1) int x2, @Local(ordinal = 2) int y1, @Local(ordinal = 3) int y2, @Local(ordinal = 4) int z1, @Local(ordinal = 5) int z2) {
        if (!(this.level.isClientSide() && this.level instanceof ClientLevel || this.level instanceof ServerLevel)) {
            //Create compatibility: Directly accessing the chunk sections is not compatible with Create Ponder level, which subclass neither client nor server levels
            return;
        }

        TrackedBlockStatePredicate blockStateFlag;
        if (tag == FluidTags.WATER) {
            blockStateFlag = BlockStateFlags.WATER;
        } else if (tag == FluidTags.LAVA) {
            blockStateFlag = BlockStateFlags.LAVA;
        } else {
            return;
        }
        int chunkX1 = x1 >> 4;
        int chunkZ1 = z1 >> 4;
        int chunkX2 = ((x2 - 1) >> 4);
        int chunkZ2 = ((z2 - 1) >> 4);
        int chunkYIndex1 = Math.max(Pos.SectionYIndex.fromBlockCoord(this.level, y1), Pos.SectionYIndex.getMinYSectionIndex(this.level));
        int chunkYIndex2 = Math.min(Pos.SectionYIndex.fromBlockCoord(this.level, y2 - 1), Pos.SectionYIndex.getMaxYSectionIndexInclusive(this.level));
        for (int chunkX = chunkX1; chunkX <= chunkX2; chunkX++) {
            for (int chunkZ = chunkZ1; chunkZ <= chunkZ2; chunkZ++) {
                ChunkAccess chunk = this.level.getChunk(chunkX, chunkZ);
                for (int chunkYIndex = chunkYIndex1; chunkYIndex <= chunkYIndex2; chunkYIndex++) {
                    LevelChunkSection section = chunk.getSections()[chunkYIndex];
                    if (((BlockCountingSection) section).lithium$mayContainAny(blockStateFlag)) {
                        //fluid found, cannot skip code
                        return;
                    }
                }
            }
        }

        //side effects of not finding a fluid:
        this.fluidHeight.put(tag, 0.0);
        cir.setReturnValue(false);
    }
}
