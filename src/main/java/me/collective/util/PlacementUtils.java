package me.collective.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

public class PlacementUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double REACH = 4.5;

    private static final List<Block> RESISTANT_BLOCKS = Arrays.asList(
        Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ENDER_CHEST,
        Blocks.RESPAWN_ANCHOR, Blocks.ENCHANTING_TABLE, Blocks.ANVIL);

    private PlacementUtils() {
    }

    public static FindItemResult findResistantBlock() {
        for (Block block : RESISTANT_BLOCKS) {
            FindItemResult result = InventoryUtil.findInHotbarOrOffhand(block.asItem());
            if (!result.found()) continue;
            return result;
        }
        return new FindItemResult(-1, 0);
    }

    public static boolean placeBlock(BlockPos pos, boolean rotate, boolean swing, boolean strictDirection) {
        FindItemResult block = findResistantBlock();
        if (!block.found()) return false;
        return placeBlock(pos, block, rotate, swing, strictDirection);
    }

    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing,
                                     boolean strictDirection) {
        if (mc.player == null || mc.level == null) return false;
        if (!block.found()) return false;
        if (!canPlace(pos, strictDirection)) return false;

        ItemStack stack = block.isOffhand()
            ? mc.player.getOffhandItem()
            : mc.player.getInventory().getItem(block.slot());

        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

        BlockHitResult hit = getSupportHit(pos, REACH);
        if (hit == null) {
            if (strictDirection) return false;
            hit = getAirPlaceHit(pos, REACH);
            if (hit == null) return false;
        }

        if (!block.isOffhand() && !InventoryUtil.swap(block.slot(), false)) return false;

        InteractionHand hand = block.getHand();
        BlockState predicted = blockItem.getBlock().defaultBlockState();
        BlockHitResult placeHit = hit;

        Runnable send = () -> {
            if (!placeHit.isInside()) {
                AirPlaceExecutor.silentPlace(placeHit, pos, predicted, hand, swing);
            } else if (hand == InteractionHand.OFF_HAND) {
                BlockHitResult wireHit = new BlockHitResult(placeHit.getLocation(), placeHit.getDirection(),
                    placeHit.getBlockPos(), false);
                AirPlaceExecutor.silentPlace(wireHit, pos, predicted, hand, swing);
            } else {
                AirPlaceExecutor.airPlace(placeHit, predicted, AirPlaceExecutor.Method.Default);
            }
        };

        if (rotate) {
            float[] rotations = RotationUtils.getLegitRotations(hit.getLocation());
            RotationUtils.getInstance().setRotationSilentInstant(null, 50, rotations[0], rotations[1]);
            send.run();
        } else {
            send.run();
        }

        return true;
    }

    public static BlockHitResult getAirPlaceHit(BlockPos pos, double reach) {
        if (mc.player == null || mc.level == null) return null;

        Vec3 eye = mc.player.getEyePosition();

        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double inset = 0.3;
        double reachSq = reach * reach;

        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            boolean visible;
            Vec3 point;

            switch (side) {
                case DOWN -> {
                    visible = eye.y < minY;
                    point = new Vec3(clamp(eye.x, minX + inset, maxX - inset), minY,
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                case UP -> {
                    visible = eye.y > maxY;
                    point = new Vec3(clamp(eye.x, minX + inset, maxX - inset), maxY,
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                case NORTH -> {
                    visible = eye.z < minZ;
                    point = new Vec3(clamp(eye.x, minX + inset, maxX - inset),
                        clamp(eye.y, minY + inset, maxY - inset), minZ);
                }
                case SOUTH -> {
                    visible = eye.z > maxZ;
                    point = new Vec3(clamp(eye.x, minX + inset, maxX - inset),
                        clamp(eye.y, minY + inset, maxY - inset), maxZ);
                }
                case WEST -> {
                    visible = eye.x < minX;
                    point = new Vec3(minX, clamp(eye.y, minY + inset, maxY - inset),
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                case EAST -> {
                    visible = eye.x > maxX;
                    point = new Vec3(maxX, clamp(eye.y, minY + inset, maxY - inset),
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                default -> {
                    visible = false;
                    point = null;
                }
            }

            if (!visible || point == null) continue;

            double dist = eye.distanceToSqr(point);
            if (dist > reachSq || dist >= bestDist) continue;

            bestDist = dist;
            best = new BlockHitResult(point, side, pos, true);
        }

        return best;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static BlockHitResult getSupportHit(BlockPos pos, double reach) {
        if (mc.player == null || mc.level == null) return null;

        Vec3 eye = mc.player.getEyePosition();

        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState state = mc.level.getBlockState(neighbor);

            if (state.canBeReplaced()
                || state.getCollisionShape(mc.level, neighbor).isEmpty()) continue;

            Direction side = dir.getOpposite();
            Vec3 normal = side.getUnitVec3();

            boolean visible = eye.subtract(Vec3.atCenterOf(neighbor)).dot(normal) > -0.05
                || (side.getAxis().isHorizontal() && eye.y >= neighbor.getY() + 0.85);

            if (!visible) continue;

            double minX = neighbor.getX();
            double minY = neighbor.getY();
            double minZ = neighbor.getZ();
            double maxX = minX + 1.0;
            double maxY = minY + 1.0;
            double maxZ = minZ + 1.0;

            double px = clamp(eye.x, minX + 0.1, maxX - 0.1);
            double py = clamp(eye.y, minY + 0.1, maxY - 0.1);
            double pz = clamp(eye.z, minZ + 0.1, maxZ - 0.1);

            switch (side) {
                case DOWN -> py = minY;
                case UP -> py = maxY;
                case NORTH -> pz = minZ;
                case SOUTH -> pz = maxZ;
                case WEST -> px = minX;
                case EAST -> px = maxX;
            }

            Vec3 hitVec = new Vec3(px, py, pz);
            double dist = eye.distanceToSqr(hitVec);
            if (dist > reach * reach || dist >= bestDist) continue;

            bestDist = dist;
            best = new BlockHitResult(hitVec, side, neighbor, false);
        }

        return best;
    }


    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;

        if (!mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
            return false;
        }

        AABB checkBox = new AABB(pos);

        for (Entity entity : mc.level.getEntities(null, checkBox)) {
            if (entity.isSpectator() || !entity.isAlive()) continue;
            return false;
        }

        return !strictDirection || getPlaceSide(pos) != null;
    }

    public static Direction getPlaceSide(BlockPos pos) {
        if (mc.level == null) return null;
        if (!mc.level.getBlockState(pos.below()).canBeReplaced()) return Direction.DOWN;

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            if (mc.level.getBlockState(neighbor).canBeReplaced()) continue;
            return side;
        }

        if (!mc.level.getBlockState(pos.above()).canBeReplaced()) return Direction.UP;

        return null;
    }

    public static int getEnderPearlSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() != Items.ENDER_PEARL) continue;
            return i;
        }

        return -1;
    }

    public static void handleContainerInput(int slot, ContainerInput actionType) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(0, slot, 0, actionType, mc.player);
        }
    }
}