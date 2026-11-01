package io.github.foundationgames.automobility.automobile.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class BaseModel extends Model {
    protected final ModelPart root;
    protected final Vector3f translation;
    protected final Vector3f rotation;
    protected final Vector3f scale;

    public static final ModelPart PART_EMPTY = new ModelPart(List.of(), Map.of());
    public static final ResourceLocation TEXTURE_SOLID = Automobility.rl("textures/solid.png");

    public BaseModel(EntityRendererProvider.Context ctx,
                     ModelDefinition.RenderMaterial material,
                     ModelLayerLocation layer,
                     Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(material.renderType);
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;

        var head = ctx.bakeLayer(layer);
        var root = getChildSafe(head, "main");
        if (root == PART_EMPTY) {
            root = head;
        }
        this.root = root;
    }

    protected static ModelPart getChildSafe(ModelPart parent, String child) {
        try {
            return parent.getChild(child);
        } catch (NoSuchElementException ex) {
            return PART_EMPTY;
        }
    }

    protected void prepare(PoseStack matrices) {
        matrices.translate(translation.x(), translation.y(), translation.z());
        matrices.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
        matrices.mulPose(Axis.XP.rotationDegrees(rotation.x()));
        matrices.mulPose(Axis.YP.rotationDegrees(rotation.y()));
        matrices.scale(scale.x(), scale.y(), scale.z());
    }

    public void setDefaultState(float tickDelta) {
    }

    @Override
    public final void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        matrices.pushPose();
        this.prepare(matrices);
        this.root.render(matrices, vertices, light, overlay, color);
        renderExtra(matrices, vertices, light, overlay, color);
        matrices.popPose();
    }

    public final void doOtherLayerRender(PoseStack matrices, MultiBufferSource consumers, int light, int overlay) {
        matrices.pushPose();
        this.prepare(matrices);
        this.renderOtherLayer(matrices, consumers, light, overlay);
        matrices.popPose();
    }

    public void renderExtra(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
    }

    public void renderOtherLayer(PoseStack matrices, MultiBufferSource consumers, int light, int overlay) {
    }
}
