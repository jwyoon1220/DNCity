package io.github.foundationgames.automobility.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.CreativeModeTab;

public interface CustomCreativeOutput {
	void provideCreativeOutput(CreativeModeTab.Output output, HolderLookup.Provider registries);
}
