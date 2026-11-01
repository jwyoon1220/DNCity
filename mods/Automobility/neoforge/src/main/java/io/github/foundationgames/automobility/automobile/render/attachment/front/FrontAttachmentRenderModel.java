package io.github.foundationgames.automobility.automobile.render.attachment.front;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import io.github.foundationgames.automobility.automobile.render.BaseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class FrontAttachmentRenderModel extends BaseModel {
    protected final ModelPart ground;
    private float groundHeight = 0;

    public FrontAttachmentRenderModel(EntityRendererProvider.Context ctx,
                                      ModelDefinition.RenderMaterial material,
                                      ModelLayerLocation layer,
                                      Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);
        this.ground = getChildSafe(ctx.bakeLayer(layer), "ground");
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        this.groundHeight = 0;
    }

    public void setRenderState(@Nullable FrontAttachment attachment, float groundHeight, float tickDelta) {
        this.groundHeight = groundHeight;
    }

    @Override
    public void renderExtra(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        if (this.ground != null) {
            matrices.pushPose();
            matrices.translate(0, groundHeight, 0);
            this.ground.render(matrices, vertices, light, overlay, color);
            matrices.popPose();
        }
    }
}
