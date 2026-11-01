package io.github.foundationgames.automobility.util;

import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.Optional;

public enum AUtils {;
    /**
     * A format for decimal values displaying the tenths
     * and hundredths place, along with the ones place and
     * optionally the tens, hundreds, and thousands places.
     */
    public static final DecimalFormat DEC_TWO_PLACES = new DecimalFormat("###0.00");

    /**
     * An array of all horizontal directions. Consists of
     * all directions except UP and DOWN.
     */
    public static final Direction[] HORIZONTAL_DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public static final StreamCodec<ByteBuf, Vec3> STREAM_CODEC_VEC3 = StreamCodec.of(
            (buf, v) -> {
                buf.writeDouble(v.x());
                buf.writeDouble(v.y());
                buf.writeDouble(v.z());
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * Shifts the number 'in' towards zero by the amount 'by'
     * @param in The number to shift
     * @param by The amount to shift it by
     * @return The result of the shift
     */
    public static float zero(float in, float by) {
        return shift(in, by, 0);
    }

    /**
     * Shifts the number 'in' towards zero by the amount 'by'
     * @param in The number to shift
     * @param by The amount to shift it by
     * @return The result of the shift
     */
    public static int zero(int in, int by) {
        return shift(in, by, 0);
    }

    /**
     * Shifts the number 'in' towards the number 'to' by the amount 'by'
     * @param in The number to shift
     * @param by The amount to shift it by
     * @param to The number to shift it to
     * @return The result of the shift
     */
    public static float shift(float in, float by, float to) {
        if ((Math.abs(in - to)) < by) return to;
        if (in > to) by *= -1;
        in += by;
        return in;
    }

    /**
     * Shifts the number 'in' towards the number 'to' by the amount 'by'
     * @param in The number to shift
     * @param by The amount to shift it by
     * @param to The number to shift it to
     * @return The result of the shift
     */
    public static int shift(int in, int by, int to) {
        if ((Math.abs(in - to)) < by) return to;
        if (in > to) by *= -1;
        in += by;
        return in;
    }

    /**
     * Returns whether the two passed values have the same sign (both negative/both positive)
     * @param a The first value
     * @param b The second value
     * @return Whether they have the same sign
     */
    public static boolean haveSameSign(float a, float b) {
        if (a == 0 || b == 0) {
            return a == b;
        }
        return a / Math.abs(a) == b / Math.abs(b);
    }

    /**
     * Between the passed values a and b, returns the one which is furthest from zero.
     * @param a The first value
     * @param b The second value
     * @return The value which is furthest away from zero
     */
    public static float furthestFromZero(float a, float b) {
        return Math.abs(a) > Math.abs(b) ? a : Math.abs(b) > Math.abs(a) ? b : a;
    }

    /**
     * Puts a Vec3d to an NbtCompound
     * @param vec The vector to be written
     * @return An NbtCompound containing the values of the passed vector
     */
    public static CompoundTag v3dToNbt(Vec3 vec) {
        var r = new CompoundTag();
        r.putDouble("x", vec.x());
        r.putDouble("y", vec.y());
        r.putDouble("z", vec.z());
        return r;
    }

    /**
     * Gets a Vec3d from an NbtCompound created by AUtils.v3dToNbt()
     * @param nbt The NbtCompound to read from
     * @return A Vec3d containing the values in the passed NbtCompound
     */
    public static Vec3 v3dFromNbt(CompoundTag nbt) {
        return new Vec3(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"));
    }

    /**
     * Turns an RGB color integer into a Vec3f.
     * @param color An RGB color integer
     * @return A Vec3f containing the color integer's RGB, with x being r, y being g, and z being b. All values are from 0 to 1.
     */
    public static Vector3f colorFromInt(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return new Vector3f((float)r / 255, (float)g / 255, (float)b / 255);
    }

    public static int colorToInt(float a, float r, float g, float b) {
        int ia = (int)(a * 255);
        int ir = (int)(r * 255);
        int ig = (int)(g * 255);
        int ib = (int)(b * 255);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    public static boolean canMerge(ItemStack a, ItemStack b) {
        return ItemStack.isSameItem(a, b) && (a.getCount() + b.getCount() <= a.getMaxStackSize());
    }

    public static byte[] arrayOf(ByteBuf buffer) {
        int sz = buffer.readableBytes();
        byte[] r = new byte[sz];
        buffer.readBytes(r);
        return r;
    }

    /**
     * Tries to insert an ItemStack into an inventory.
     *
     * @param stack The item to insert
     * @param inv   The inventory to insert into
     * @return {@code true} if the entire stack was consumed, or {@code false} if some or all of the stack is remaining
     */
    public static boolean transferInto(ItemStack stack, Container inv) {
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (inv.canPlaceItem(slot, stack)) {
                var slotStack = inv.getItem(slot);
                if (slotStack.isEmpty()) {
                    inv.setItem(slot, stack);
                    return true;
                }
                if (canMerge(slotStack, stack)) {
                    int amount = Math.min(stack.getCount(), stack.getMaxStackSize() - slotStack.getCount());
                    stack.shrink(amount);
                    slotStack.grow(amount);
                    return stack.isEmpty();
                }
            }
        }
        return false;
    }

    public static ItemStack createGroupIcon() {
        return new ItemStack(AutomobilityItems.CROWBAR.require());
    }

    public static ItemStack createPrefabsIcon() {
        return new AutomobileData(Optional.empty(), AutomobileFrame.STANDARD_LIGHT_BLUE, AutomobileWheel.STANDARD, AutomobileEngine.IRON).asStack();
    }
}
