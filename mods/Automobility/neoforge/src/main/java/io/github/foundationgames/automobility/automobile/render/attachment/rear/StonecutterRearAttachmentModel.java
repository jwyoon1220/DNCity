package io.github.foundationgames.automobility.automobile.render.attachment.rear;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class StonecutterRearAttachmentModel extends RearAttachmentRenderModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile/rear_attachment/stonecutter"), "main");

    private final ModelPart blade;

    public StonecutterRearAttachmentModel(EntityRendererProvider.Context ctx,
                                          ModelDefinition.RenderMaterial material,
                                          ModelLayerLocation layer,
                                          Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);
        this.blade = getChildSafe(this.root, "blade");
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        this.blade.setRotation(0, 0, 0);
    }

    @Override
    public void setRenderState(@Nullable RearAttachment attachment, float wheelAngle, float tickDelta) {
        super.setRenderState(attachment, wheelAngle, tickDelta);

        if (this.blade != null) {
            this.blade.setRotation(wheelAngle * 0.45f, 0, 0);
        }
    }
}
