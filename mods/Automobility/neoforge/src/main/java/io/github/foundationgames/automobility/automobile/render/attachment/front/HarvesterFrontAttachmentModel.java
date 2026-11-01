package io.github.foundationgames.automobility.automobile.render.attachment.front;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class HarvesterFrontAttachmentModel extends FrontAttachmentRenderModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile/front_attachment/harvester"), "main");

    private final ModelPart roller;

    public HarvesterFrontAttachmentModel(EntityRendererProvider.Context ctx,
                                         ModelDefinition.RenderMaterial material,
                                         ModelLayerLocation layer,
                                         Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);
        this.roller = getChildSafe(this.ground, "roller");
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        this.roller.setRotation(0, 0, 0);
    }

    @Override
    public void setRenderState(@Nullable FrontAttachment attachment, float groundHeight, float tickDelta) {
        super.setRenderState(attachment, groundHeight, tickDelta);

        if (this.roller != null) {
            if (attachment != null) {
                this.roller.setRotation((float) Math.toRadians(attachment.automobile().getWheelAngle(tickDelta)), 0, 0);
            } else {
                this.roller.setRotation(0, 0, 0);
            }
        }
    }
}
