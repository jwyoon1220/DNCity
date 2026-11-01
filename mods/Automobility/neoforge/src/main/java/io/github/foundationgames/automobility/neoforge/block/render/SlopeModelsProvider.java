package io.github.foundationgames.automobility.neoforge.block.render;

import io.github.foundationgames.automobility.block.model.SlopeUnbakedModel;
import io.github.foundationgames.automobility.util.InitlessConstants;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class SlopeModelsProvider extends BlockModelProvider {
    public SlopeModelsProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, InitlessConstants.AUTOMOBILITY, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (var id : SlopeUnbakedModel.DEFAULT_MODELS.keySet()) {
            this.getBuilder(id.toString()).customLoader((model, files) ->
                    new NeoForgeSlopeGeometryLoader.Builder<>(model, files, id));
        }
    }
}
