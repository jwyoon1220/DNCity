package io.github.foundationgames.automobility.util.duck;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface CollisionArea {
    boolean isPointInside(double x, double y, double z);

    boolean boxIntersects(AABB box);

    double highestY(double x, double y, double z);

    static CollisionArea box(double ax, double ay, double az, double bx, double by, double bz) {
        return (CollisionArea) new AABB(ax, ay, az, bx, by, bz);
    }

    static CollisionArea entity(Entity entity) {
        if (entity instanceof CollisionArea col) {
            return col;
        }

        return (CollisionArea) entity.getBoundingBox();
    }

    record SlopeArea(AABB box, double xSlope, double zSlope, double originY) implements CollisionArea {
        @Override
        public boolean isPointInside(double x, double y, double z) {
            return ((CollisionArea) this.box()).isPointInside(x, y, z);
        }

        @Override
        public boolean boxIntersects(AABB box) {
            return ((CollisionArea) this.box()).boxIntersects(box);
        }

        @Override
        public double highestY(double x, double y, double z) {
            var planeOrigin = this.box().getBottomCenter().add(0, (this.box().getYsize() - 1) + this.originY(), 0);
            var originToPoint = new Vec3(x - planeOrigin.x(), y - planeOrigin.y(), z - planeOrigin.z());

            return planeOrigin.y() + (originToPoint.x() * this.xSlope()) + (originToPoint.z() * this.zSlope());
        }
    }
}
