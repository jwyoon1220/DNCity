package io.github.foundationgames.automobility.automobile.model;

import com.mojang.serialization.Codec;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.render.BaseModel;
import io.github.foundationgames.automobility.automobile.render.attachment.front.AutopilotFrontAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.attachment.front.FrontAttachmentRenderModel;
import io.github.foundationgames.automobility.automobile.render.attachment.front.HarvesterFrontAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.attachment.rear.BannerPostRearAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.attachment.rear.ChestRearAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.attachment.rear.GrindstoneRearAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.attachment.rear.PlowRearAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.attachment.rear.RearAttachmentRenderModel;
import io.github.foundationgames.automobility.automobile.render.attachment.rear.StonecutterRearAttachmentModel;
import io.github.foundationgames.automobility.automobile.render.obj.ObjModel;
import io.github.foundationgames.automobility.util.SimpleMapContentRegistry;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public record ModelType(ResourceLocation id,
                        ModelInstanceProvider provider
) implements SimpleMapContentRegistry.Identifiable {
    public static final SimpleMapContentRegistry<ModelType> REGISTRY = new SimpleMapContentRegistry<>();
    public static final Codec<ModelType> CODEC = REGISTRY.codec();

    public static final ModelType BASIC = REGISTRY.register(new ModelType(Automobility.rl("basic"), BaseModel::new));
    public static final ModelType FRONT_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("front_attachment"), FrontAttachmentRenderModel::new));
    public static final ModelType AUTOPILOT_FRONT_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("autopilot_front_attachment"), AutopilotFrontAttachmentModel::new));
    public static final ModelType HARVESTER_FRONT_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("harvester_front_attachment"), HarvesterFrontAttachmentModel::new));
    public static final ModelType REAR_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("rear_attachment"), RearAttachmentRenderModel::new));
    public static final ModelType CHEST_REAR_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("chest_rear_attachment"), ChestRearAttachmentModel::new));
    public static final ModelType GRINDSTONE_REAR_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("grindstone_rear_attachment"), GrindstoneRearAttachmentModel::new));
    public static final ModelType STONECUTTER_REAR_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("stonecutter_rear_attachment"), StonecutterRearAttachmentModel::new));
    public static final ModelType PLOW_REAR_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("plow_rear_attachment"), PlowRearAttachmentModel::new));
    public static final ModelType BANNER_REAR_ATTACHMENT = REGISTRY.register(new ModelType(Automobility.rl("banner_rear_attachment"), BannerPostRearAttachmentModel::new));
    public static final ModelType OBJ = REGISTRY.register(new ModelType(Automobility.rl("obj"), ObjModel::new));

    @Override
    public ResourceLocation getId() {
        return id();
    }

    public interface ModelInstanceProvider {
        Model create(EntityRendererProvider.Context ctx,
                     ModelDefinition.RenderMaterial material,
                     ModelLayerLocation modelLayer,
                     Vector3f translation, Vector3f rotation, Vector3f scale);
    }
}
