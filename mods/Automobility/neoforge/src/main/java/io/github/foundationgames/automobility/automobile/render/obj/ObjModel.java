package io.github.foundationgames.automobility.automobile.render.obj;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ObjModel extends Model {
    private final Vector3f translation;
    private final Vector3f rotation;
    private final Vector3f scale;

    private final Supplier<BakedObj> obj;

    public ObjModel(EntityRendererProvider.Context ctx,
                     ModelDefinition.RenderMaterial material,
                     ModelLayerLocation layer,
                     Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(material.renderType);
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;

        this.obj = ObjLoader.INSTANCE.getObj(layer);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        var obj = this.obj.get();
        if (obj == null) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(translation.x(), translation.y(), translation.z());
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation.x()));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation.y()));
        poseStack.scale(scale.x(), scale.y(), scale.z());
        var pose = poseStack.last();

        var vtx = new Vector3f();
        var nml = new Vector3f();
        var uv = new Vector2f();

        int fi = 0;
        for (int i = 0; i < obj.idVertex().length; i++) {
            int iv = obj.idVertex()[i] * 3;
            int in = obj.idNormal()[i] * 3;
            int iu = obj.idUv()[i] * 2;

            vtx.set(obj.vertex()[iv], obj.vertex()[iv + 1], obj.vertex()[iv + 2]);
            nml.set(obj.normal()[in], obj.normal()[in + 1], obj.normal()[in + 2]);
            uv.set(obj.uv()[iu], 1.0 - obj.uv()[iu + 1]);

            int r = 1;
            if (++fi >= 3) {
                r = 2;
                fi = 0;
            }

            for (int j = 0; j < r; j++) {
                buffer.addVertex(pose, vtx.x(), vtx.y(), vtx.z())
                        .setColor(color)
                        .setUv(uv.x(), uv.y())
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(pose, nml.x(), nml.y(), nml.z());
            }
        }

        poseStack.popPose();
    }
}
