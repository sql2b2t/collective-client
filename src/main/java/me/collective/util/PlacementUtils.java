package me.collective.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;

public class PlacementUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
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
        if (mc.player == null || mc.world == null) return false;
        if (!block.found()) return false;
        if (!canPlace(pos, strictDirection)) return false;

        ItemStack stack = block.isOffhand()
            ? mc.player.getOffHandStack()
            : mc.player.getInventory().getStack(block.slot());

        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

        BlockHitResult hit = getSupportHit(pos, REACH);
        if (hit == null) {
            if (strictDirection) return false;
            hit = getAirPlaceHit(pos, REACH);
            if (hit == null) return false;
        }

        if (!block.isOffhand() && !InventoryUtil.swap(block.slot(), false)) return false;

        Hand hand = block.getHand();
        BlockState predicted = blockItem.getBlock().getDefaultState();
        BlockHitResult placeHit = hit;

        Runnable send = () -> {
            if (!placeHit.isInsideBlock()) {
                AirPlaceExecutor.silentPlace(placeHit, pos, predicted, hand, swing);
            } else if (hand == Hand.OFF_HAND) {
                BlockHitResult wireHit = new BlockHitResult(placeHit.getPos(), placeHit.getSide(),
                    placeHit.getBlockPos(), false);
                AirPlaceExecutor.silentPlace(wireHit, pos, predicted, hand, swing);
            } else {
                AirPlaceExecutor.airPlace(placeHit, predicted, AirPlaceExecutor.Method.Default);
            }
        };

        if (rotate) {
            float[] rotations = RotationUtils.getLegitRotations(hit.getPos());
            RotationUtils.getInstance().setRotationSilentInstant(null, 50, rotations[0], rotations[1]);
            send.run();
        } else {
            send.run();
        }

        return true;
    }

    public static BlockHitResult getAirPlaceHit(BlockPos pos, double reach) {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eye = mc.player.getEyePos();

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
            Vec3d point;

            switch (side) {
                case DOWN -> {
                    visible = eye.y < minY;
                    point = new Vec3d(clamp(eye.x, minX + inset, maxX - inset), minY,
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                case UP -> {
                    visible = eye.y > maxY;
                    point = new Vec3d(clamp(eye.x, minX + inset, maxX - inset), maxY,
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                case NORTH -> {
                    visible = eye.z < minZ;
                    point = new Vec3d(clamp(eye.x, minX + inset, maxX - inset),
                        clamp(eye.y, minY + inset, maxY - inset), minZ);
                }
                case SOUTH -> {
                    visible = eye.z > maxZ;
                    point = new Vec3d(clamp(eye.x, minX + inset, maxX - inset),
                        clamp(eye.y, minY + inset, maxY - inset), maxZ);
                }
                case WEST -> {
                    visible = eye.x < minX;
                    point = new Vec3d(minX, clamp(eye.y, minY + inset, maxY - inset),
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                case EAST -> {
                    visible = eye.x > maxX;
                    point = new Vec3d(maxX, clamp(eye.y, minY + inset, maxY - inset),
                        clamp(eye.z, minZ + inset, maxZ - inset));
                }
                default -> {
                    visible = false;
                    point = null;
                }
            }

            if (!visible || point == null) continue;

            double dist = eye.squaredDistanceTo(point);
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
        if (mc.player == null || mc.world == null) return null;

        Vec3d eye = mc.player.getEyePos();

        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);

            if (state.isReplaceable()
                || state.getCollisionShape(mc.world, neighbor).isEmpty()) continue;

            Direction side = dir.getOpposite();
            Vec3d normal = Vec3d.of(side.getVector());
            Vec3d hitVec = Vec3d.ofCenter(neighbor).add(normal.multiply(0.5));

            if (eye.subtract(hitVec).dotProduct(normal) <= 0.0) continue;

            double dist = eye.squaredDistanceTo(hitVec);
            if (dist > reach * reach || dist >= bestDist) continue;

            bestDist = dist;
            best = new BlockHitResult(hitVec, side, neighbor, false);
        }

        return best;
    }

    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (mc.world == null) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;

        if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent())) {
            return false;
        }

        Box checkBox = Box.from(Vec3d.ofCenter(pos));

        for (Entity entity : mc.world.getOtherEntities(null, checkBox)) {
            if (entity.isSpectator() || !entity.isAlive()) continue;
            return false;
        }

        return !strictDirection || getPlaceSide(pos) != null;
    }

    public static Direction getPlaceSide(BlockPos pos) {
        if (mc.world == null) return null;
        if (!mc.world.getBlockState(pos.down()).isReplaceable()) return Direction.DOWN;

        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(side);
            if (mc.world.getBlockState(neighbor).isReplaceable()) continue;
            return side;
        }

        if (!mc.world.getBlockState(pos.up()).isReplaceable()) return Direction.UP;

        return null;
    }

    public static int getEnderPearlSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.ENDER_PEARL) continue;
            return i;
        }

        return -1;
    }

    public static void clickSlot(int slot, SlotActionType actionType) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(0, slot, 0, actionType, mc.player);
        }
    }
}
