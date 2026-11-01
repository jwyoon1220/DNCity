package io.github.foundationgames.automobility.automobile.render.attachment.rear;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class GrindstoneRearAttachmentModel extends RearAttachmentRenderModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile/rear_attachment/grindstone"), "main");

    private final ModelPart grindstone;

    public GrindstoneRearAttachmentModel(EntityRendererProvider.Context ctx,
                                         ModelDefinition.RenderMaterial material,
                                         ModelLayerLocation layer,
                                         Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);
        this.grindstone = getChildSafe(this.root, "grindstone");
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        this.grindstone.setRotation(0, 0, 0);
    }

    @Override
    public void setRenderState(@Nullable RearAttachment attachment, float wheelAngle, float tickDelta) {
        super.setRenderState(attachment, wheelAngle, tickDelta);

        if (this.grindstone != null) {
            this.grindstone.setRotation(wheelAngle * 0.25f, 0, 0);
        }
    }
}
