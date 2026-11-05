package net.caffeinemc.mods.sodium.client.render.viewport;

public interface ViewportProvider {
    Viewport sodium$createViewport();

    /**
     * The camera-relative clip matrix (projection * modelView) this frustum was built from. Its origin is the real
     * camera, not the position {@code Frustum#offsetToFullyIncludeCameraCube} may have moved the frustum's own
     * camera position to. Null if unknown; a default method so frustum classes of other mods that implement this
     * interface themselves (Iris's shadow frustums) keep compiling. Vanilla's {@code Frustum} gets the real one
     * from {@code FrustumMixin}, which subclasses inherit.
     */
    default org.joml.Matrix4fc sodium$getClipMatrix() {
        return null;
    }
}
