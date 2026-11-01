/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

@Mixin(GameRenderer.class)
public interface GameRendererPostAccessor {
    @Invoker("loadEffect")
    void firstaid$loadEffect(ResourceLocation location);

    @Invoker("shutdownEffect")
    void firstaid$shutdownEffect();

    @Accessor("postEffect")
    @Nullable
    PostChain firstaid$getPostEffect();
}
