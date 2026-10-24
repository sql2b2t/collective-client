package me.collective.util.printer;

import me.collective.util.RotationUtils;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Симуляция контекста установки блока (порт из BepHax NEW-SRC).
 * Подменяет «взгляд» игрока на симулированный yaw/pitch, чтобы блок ставился
 * с правильной ориентацией без реального поворота камеры.
 *
 * Адаптация под 1.21.11 (Yarn): Mojmap-переопределения BlockPlaceContext
 * отображаются на getPlayerYaw/getHorizontalPlayerFacing (ItemUsageContext) и
 * getPlayerLookDirection/getVerticalPlayerLookDirection/getPlacementDirections
 * (ItemPlacementContext); Direction.fromYRot -> Direction.fromHorizontalDegrees,
 * replacingClickedOnBlock -> canReplaceExisting, getClickedFace -> getSide.
 */
public class PlacementContextSim extends ItemPlacementContext {
    private final float simYaw;
    private final Vec3 look;

    public PlacementContextSim(Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit, float yaw, float pitch) {
        super(player, hand, stack, hit);
        this.simYaw = yaw;
        this.look = RotationUtils.getRotationVector(pitch, yaw);
    }

    private static Vec3 normal(Direction d) {
        return new Vec3(d.getOffsetX(), d.getOffsetY(), d.getOffsetZ());
    }

    private boolean ready() {
        return this.look != null;
    }

    private Direction[] orderedByNearest() {
        if (!this.ready()) {
            return Direction.getEntityFacingOrder(this.getPlayer());
        }

        Direction[] dirs = Direction.values().clone();
        Arrays.sort(dirs, Comparator.comparingDouble(d -> -this.look.dotProduct(normal(d))));
        return dirs;
    }

    @Override
    public float getPlayerYaw() {
        return this.ready() ? this.simYaw : super.getPlayerYaw();
    }

    @Override
    public Direction getHorizontalPlayerFacing() {
        return this.ready() ? Direction.fromHorizontalDegrees(this.simYaw) : super.getHorizontalPlayerFacing();
    }

    @Override
    public Direction getPlayerLookDirection() {
        return this.orderedByNearest()[0];
    }

    @Override
    public Direction getVerticalPlayerLookDirection() {
        if (!this.ready()) {
            return super.getVerticalPlayerLookDirection();
        } else {
            return this.look.y > 0.0 ? Direction.UP : Direction.DOWN;
        }
    }

    @Override
    public Direction[] getPlacementDirections() {
        if (!this.ready()) {
            return super.getPlacementDirections();
        }

        Direction[] dirs = this.orderedByNearest();
        if (this.canReplaceExisting()) {
            return dirs;
        }

        Direction opposite = this.getSide().getOpposite();
        int i = 0;

        while (i < dirs.length && dirs[i] != opposite) {
            i++;
        }

        if (i > 0) {
            System.arraycopy(dirs, 0, dirs, 1, i);
            dirs[0] = opposite;
        }

        return dirs;
    }
}