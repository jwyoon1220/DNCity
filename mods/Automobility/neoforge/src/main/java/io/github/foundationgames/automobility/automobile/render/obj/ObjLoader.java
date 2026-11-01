package io.github.foundationgames.automobility.automobile.render.obj;

import de.javagl.obj.ObjReader;
import io.github.foundationgames.automobility.Automobility;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ObjLoader implements ResourceManagerReloadListener {
    public static ObjLoader INSTANCE = new ObjLoader();
    public static final ResourceLocation ID = Automobility.rl("obj_loader");

    public static final Logger LOG = LogManager.getLogger("Automobility | OBJ Loader");
    private final Map<ModelLayerLocation, BakedObj> objs = new HashMap<>();

    public Supplier<BakedObj> getObj(ModelLayerLocation location) {
        return new EventualObj(location);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.objs.clear();

        new FileToIdConverter("models/entity", ".obj").listMatchingResources(resourceManager).forEach((rl, res) -> {
            try {
                var fullPath = rl.getPath().replaceFirst("models/entity/", "");
                var splitPath = fullPath.split("/");
                var dirs = new String[splitPath.length - 1];
                System.arraycopy(splitPath, 0, dirs, 0, dirs.length);
                var layerName = splitPath[splitPath.length - 1].replace(".obj", "");
                var modelName = String.join("/", dirs);
                var layer = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), modelName), layerName);

                try (var in = res.open()) {
                    var obj = ObjReader.read(in);
                    this.objs.put(layer, BakedObj.bake(obj));
                }
            } catch (ResourceLocationException | IOException ex) {
                LOG.error(ex);
            }
        });
    }

    public class EventualObj implements Supplier<BakedObj> {
        public final ModelLayerLocation layer;
        private @Nullable BakedObj obj = null;

        public EventualObj(ModelLayerLocation layer) {
            this.layer = layer;
        }

        @Override
        public BakedObj get() {
            if (obj == null) {
                obj = ObjLoader.this.objs.get(this.layer);
            }

            return obj;
        }
    }
}
