package io.github.foundationgames.automobility.automobile.attachment.front;

import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

public class MobControllerFrontAttachment extends FrontAttachment {
    public MobControllerFrontAttachment(FrontAttachmentType<?> type, AutomobileEntity automobile) {
        super(type, automobile);
    }

    @Override
    public boolean canDrive(Entity entity) {
        return super.canDrive(entity) || (entity instanceof Mob);
    }

    @Override
    public boolean isProvidingAlternativeInputs(AutomobileEntity automobile, @Nullable Entity driver) {
        return automobile.isDriving(driver) && driver instanceof Mob;
    }

    @Override
    public void provideAlternativeInputs(AutomobileEntity auto, AutomobileEntity.Input input, @Nullable Entity e) {
        if (!(e instanceof Mob driver)) return;

        // Don't move if the driver doesn't exist or can't drive
        if (driver.isDeadOrDying() || driver.isRemoved()) {
            if (input.setInputs(false, false, 0, false, false)) {
                auto.markDirty();
            }
            return;
        }

        var path = driver.getNavigation().getPath();
        // checks if there is a current, incomplete path that the entity has targeted
        if (path != null && !path.isDone() && path.getEndNode() != null) {
            // determines the relative position to drive to, based on the end of the path
            var pos = path.getEndNode().asVec3().subtract(auto.position());
            // determines the angle to that position
            double target = Mth.wrapDegrees(Math.toDegrees(Math.atan2(pos.x(), pos.z())));
            // determines another relative position, this time to the path's current node (in the case of the path directly to the end being obstructed)
            var fnPos = path.getNextNode().asVec3().subtract(auto.position());
            // determines the angle to that current node's position
            double fnTarget = Mth.wrapDegrees(Math.toDegrees(Math.atan2(fnPos.x(), fnPos.z())));
            // if the difference in angle between the end position and the current node's position is too great,
            // the automobile will drive to that current node under the assumption that the path directly to the
            // end is obstructed
            if (Math.abs(target - fnTarget) > 69) {
                pos = fnPos;
                target = fnTarget;
            }
            // fixes up the automobile's own yaw value
            float yaw = Mth.wrapDegrees(-auto.getYRot());
            // finds the difference between the target angle and the yaw
            double offset = Mth.wrapDegrees(yaw - target);
            // whether the automobile should go in reverse
            boolean reverse = false;
            // a value to determine the threshold used to determine whether the automobile is moving
            // both slow enough and is at an extreme enough offset angle to incrementally move in reverse
            float mul = 0.5f + (Mth.clamp(auto.getHSpeed(), 0, 1) * 0.5f);
            if (pos.length() < 20 * mul && Math.abs(offset) > 180 - (170 * mul)) {
                long time = auto.level().getGameTime();
                // this is so that the automobile alternates between reverse and forward,
                // like a driver would do in order to angle their vehicle toward a target location
                reverse = (time % 80 <= 30);
            }
            // set the accel/brake inputs
            input.accelerating = !reverse;
            input.braking = reverse;
            float steer = reverse ? 1 : -1;
            // set the steering inputs, with a bit of a dead zone to prevent jittering
            if (offset < -10) {
                input.steering = steer;
            } else if (offset > 10) {
                input.steering = -steer;
            }
            auto.markDirty();
        } else if (input.setInputs(false, false, 0, false, false)) {
            auto.markDirty();
        }
    }
}
