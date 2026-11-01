package com.tacz.guns.api.event.server;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 子弹击中方块时触发的事件，目前仅在服务端触发
 */
public class AmmoHitBlockEvent extends Event implements KubeJSGunEventPoster<AmmoHitBlockEvent>, ICancellableEvent {
    private final Level level;
    private final BlockHitResult hitResult;
    private final BlockState state;
    private final EntityKineticBullet ammo;

    public AmmoHitBlockEvent(Level level, BlockHitResult hitResult, BlockState state, EntityKineticBullet ammo) {
        this.level = level;
        this.hitResult = hitResult;
        this.state = state;
        this.ammo = ammo;
        postServerEventToKubeJS(this);
    }

    public Level getLevel() {
        return level;
    }

    public BlockHitResult getHitResult() {
        return hitResult;
    }

    public BlockState getState() {
        return state;
    }

    public EntityKineticBullet getAmmo() {
        return ammo;
    }

    /**
     * A listener says the bullet went through the block: it leaves at {@code exit} (the point where its flight line leaves the
     * block's shape) with its speed scaled by {@code speedFactor} (0..1]. The bullet then keeps tracing the rest of this tick's
     * path from {@code exit}, so it can hit what lies behind the block in the same tick. Leaves the event uncancelled; do not
     * cancel it as well.
     */
    public void setPenetrated(Vec3 exit, float speedFactor) {
        if (!(speedFactor > 0f && speedFactor <= 1f)) {
            throw new IllegalArgumentException("speed factor must be in (0, 1], was " + speedFactor);
        }
        this.penetrationExit = exit;
        this.residualSpeedFactor = speedFactor;
    }

    /** Where the bullet left the block, or null if no listener said it went through. */
    @Nullable
    public Vec3 getPenetrationExit() {
        return penetrationExit;
    }

    public float getResidualSpeedFactor() {
        return residualSpeedFactor;
    }

    @Nullable
    private Vec3 penetrationExit = null;
    private float residualSpeedFactor = 1f;
}
