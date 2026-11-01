package io.github.foundationgames.automobility.automobile.render.attachment.rear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.rear.BannerPostRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class BannerPostRearAttachmentModel extends RearAttachmentRenderModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile/rear_attachment/banner_post"), "main");

    private final ModelPart fakePole;
    private final ModelPart pole;
    private final ModelPart bar;

    private final ModelPart flagPole;
    private final ModelPart flagBar;
    private final ModelPart flag;

    private boolean renderPole;
    private DyeColor baseColor;
    private BannerPatternLayers patterns;

    public BannerPostRearAttachmentModel(EntityRendererProvider.Context ctx,
                                         ModelDefinition.RenderMaterial material,
                                         ModelLayerLocation layer,
                                         Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);

        this.fakePole = getChildSafe(this.root, "fake_pole");
        this.pole = getChildSafe(this.root, "pole");
        this.bar = getChildSafe(this.pole, "bar");

        this.flagPole = getChildSafe(this.root, "flag_pole");
        this.flagBar = getChildSafe(this.flagPole, "flag_bar");
        this.flag = getChildSafe(this.flagBar, "flag");

        this.flagPole.visible = false;
        this.pole.visible = false;
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        this.fakePole.visible = true;
    }

    @Override
    public void setRenderState(@Nullable RearAttachment attachment, float wheelAngle, float tickDelta) {
        super.setRenderState(attachment, wheelAngle, tickDelta);

        float push = attachment == null ? 0 : (float) Math.pow(Math.max(0, attachment.automobile().getHSpeed() * 0.368f), 2);
        this.pole.xRot = -push;
        this.bar.xRot = push;
        this.flagPole.xRot = -push;
        this.flagBar.xRot = push;

        if (attachment instanceof BannerPostRearAttachment bannerPost) {
            this.baseColor = bannerPost.getBaseColor();
            this.patterns = bannerPost.getPatterns();

            this.flag.setRotation(push, this.flag.yRot, 0.05f * (float)Math.sin((attachment.automobile().getTime() + tickDelta) / 20));
        }

        this.renderPole = attachment != null;
        this.fakePole.visible = false;
    }

    @Override
    public void renderExtra(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        if (this.renderPole) {
            this.pole.visible = true;
            matrices.pushPose();

            matrices.translate(0, -1f, 0);
            matrices.scale(0.666f, 0.666f, 0.666f);
            matrices.translate(0, 1f, 0);
            this.pole.render(matrices, vertices, light, overlay, color);

            matrices.popPose();
            this.pole.visible = false;
            this.renderPole = false;
        }
        this.fakePole.visible = true;
    }

    @Override
    public void renderOtherLayer(PoseStack matrices, MultiBufferSource consumers, int light, int overlay) {
        if (this.baseColor != null && this.patterns != null) {
            this.flagPole.visible = true;
            matrices.pushPose();

            matrices.translate(0, -1f, 0);
            matrices.scale(0.666f, 0.666f, 0.666f);
            matrices.translate(0, 1f, 0);
            BannerRenderer.renderPatterns(matrices, consumers, light, overlay, this.flagPole, ModelBakery.BANNER_BASE, true, baseColor, this.patterns);

            matrices.popPose();
            this.flagPole.visible = false;
            this.baseColor = null;
            this.patterns = null;
        }
    }
}
