package io.github.foundationgames.automobility.automobile.render.obj;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjUtils;

public record BakedObj(int[] idVertex, int[] idNormal, int[] idUv, float[] vertex, float[] normal, float[] uv) {
    public static BakedObj bake(Obj resource) {
        var obj = ObjUtils.triangulate(resource);

        return new BakedObj(
                ObjData.getFaceVertexIndicesArray(obj),
                ObjData.getFaceNormalIndicesArray(obj),
                ObjData.getFaceTexCoordIndicesArray(obj),
                ObjData.getVerticesArray(obj),
                ObjData.getNormalsArray(obj),
                ObjData.getTexCoordsArray(obj, 2)
        );
    }
}
