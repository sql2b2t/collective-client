package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.GrimUtils;
import me.collective.util.PacketUtil;
import me.collective.util.RotationUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static me.collective.util.HighwayUtils.forward;
import static me.collective.util.HighwayUtils.left;
import static me.collective.util.HighwayUtils.right;

/**
 * Базовый модуль автоматического бурения хайвеев и тоннелей.
 * Поддерживает безопасный обход проверок GrimAC (дистанция, углы обзора, последовательности тиков).
 */
public abstract class BorerModule extends Module {
    public enum Shape {
        THIN,
        HIGHWAY
    }

    public final EnumSetting<Shape> mode = add(new EnumSetting<>("Shape", Shape.HIGHWAY));
    public final BooleanSetting grimBypass = add(new BooleanSetting("GrimBypass", true));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    public final NumberSetting maxPerTick = add(new NumberSetting("MaxPerTick", 1, 1, 3, 1));

    public final NumberSetting extForward;
    public final NumberSetting extBackward;
    public final NumberSetting xOffset;
    public final NumberSetting zOffset;

    public final NumberSetting keepY = add(new NumberSetting("KeepY", 119, -1, 255, 1));

    protected int packets = 0;
    protected long lastUpdateTime = 0;
    protected BlockPos playerPos = BlockPos.ZERO;

    private final Set<BlockPos> pendingBlocks = new LinkedHashSet<>();
    private BlockPos currentMiningPos = null;
    private float currentMiningDamage = 0.0f;

    protected BorerModule(String name, String description, int extForwards, int extBackwards, int xOff, int zOff) {
        super(name, description, Category.HIGHWAY);

        extForward = add(new NumberSetting("ExtForward", extForwards, 1, 6, 1));
        extBackward = add(new NumberSetting("ExtBackward", extBackwards, 1, 6, 1));
        this.xOffset = add(new NumberSetting("XOffset", xOff, -2, 2, 1));
        this.zOffset = add(new NumberSetting("ZOffset", zOff, -2, 2, 1));
    }

    @Override
    public void onDisable() {
        pendingBlocks.clear();
        currentMiningPos = null;
        currentMiningDamage = 0.0f;
    }

    @EventHandler
    public abstract void onTick(TickEvent.Pre event);

    @EventHandler
    public void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || !isToggled()) return;

        if (grimBypass.getValue()) {
            processGrimQueue();
        }
    }

    protected void do2x3(BlockPos playerPos) {
        IntStream.rangeClosed(-extBackward.getIntValue(), extForward.getIntValue()).forEach(i -> {
            breakBlock(forward(playerPos, i));
            breakBlock(forward(playerPos, i).above());
            breakBlock(forward(playerPos, i).above(2));
            breakBlock(left(forward(playerPos, i), 1));
            breakBlock(left(forward(playerPos, i), 1).above());
            breakBlock(left(forward(playerPos, i), 1).above(2));
        });
    }

    protected void doHighway4(BlockPos playerPos) {
        IntStream.rangeClosed(-extBackward.getIntValue(), extForward.getIntValue()).forEach(i -> {
            breakBlock(forward(playerPos, i));
            breakBlock(forward(playerPos, i).above());
            breakBlock(forward(playerPos, i).above(2));
            breakBlock(forward(playerPos, i).above(3));

            breakBlock(right(forward(playerPos, i), 1));
            breakBlock(right(forward(playerPos, i), 1).above());
            breakBlock(right(forward(playerPos, i), 1).above(2));
            breakBlock(right(forward(playerPos, i), 1).above(3));

            breakBlock(right(forward(playerPos, i), 2).above());
            breakBlock(right(forward(playerPos, i), 2).above(2));
            breakBlock(right(forward(playerPos, i), 2).above(3));

            breakBlock(left(forward(playerPos, i), 1));
            breakBlock(left(forward(playerPos, i), 1).above());
            breakBlock(left(forward(playerPos, i), 1).above(2));
            breakBlock(left(forward(playerPos, i), 1).above(3));

            breakBlock(left(forward(playerPos, i), 2));
            breakBlock(left(forward(playerPos, i), 2).above());
            breakBlock(left(forward(playerPos, i), 2).above(2));
            breakBlock(left(forward(playerPos, i), 2).above(3));

            breakBlock(left(forward(playerPos, i), 3).above());
            breakBlock(left(forward(playerPos, i), 3).above(2));
            breakBlock(left(forward(playerPos, i), 3).above(3));
        });
    }

    protected void breakBlock(BlockPos blockPos) {
        if (mc.player == null || mc.level == null) return;
        if (mc.level.getBlockState(blockPos).canBeReplaced()) return;

        if (grimBypass.getValue()) {
            pendingBlocks.add(blockPos.immutable());
            return;
        }

        if (packets >= 130) return;

        Direction dir = getInteractDirection(blockPos);
        PacketUtil.sendSilently(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, dir));
        PacketUtil.sendSilently(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, dir));
        packets += 2;

        mc.player.getInventory().tick();
    }

    private void processGrimQueue() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        // Если сейчас копается прочный блок (базальт, руда и т.д.)
        if (currentMiningPos != null) {
            BlockState state = mc.level.getBlockState(currentMiningPos);
            double distSq = GrimUtils.closestEyeDistanceSqTo(mc.player, currentMiningPos);
            if (state.canBeReplaced() || distSq > 18.0) {
                currentMiningPos = null;
                currentMiningDamage = 0.0f;
            } else {
                if (rotate.getValue()) {
                    rotateTo(currentMiningPos);
                }
                currentMiningDamage += state.getDestroyProgress(mc.player, mc.level, currentMiningPos);
                if (mc.getConnection() != null) {
                    mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
                }

                if (currentMiningDamage >= 1.0f) {
                    Direction dir = getInteractDirection(currentMiningPos);
                    int seq = GrimUtils.nextSequence();
                    if (mc.getConnection() != null) {
                        mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, currentMiningPos, dir, seq));
                    }
                    mc.gameMode.destroyBlock(currentMiningPos);
                    pendingBlocks.remove(currentMiningPos);
                    currentMiningPos = null;
                    currentMiningDamage = 0.0f;
                }
                return;
            }
        }

        if (pendingBlocks.isEmpty()) return;

        // Удаляем из очереди воздух и блоки за пределами досягаемости
        pendingBlocks.removeIf(bp -> mc.level.getBlockState(bp).canBeReplaced()
            || GrimUtils.closestEyeDistanceSqTo(mc.player, bp) > 18.0);

        if (pendingBlocks.isEmpty()) return;

        List<BlockPos> sorted = new ArrayList<>(pendingBlocks);
        sorted.sort(Comparator.comparingDouble(bp -> GrimUtils.closestEyeDistanceSqTo(mc.player, bp)));

        int budget = maxPerTick.getIntValue();
        int done = 0;

        for (BlockPos bp : sorted) {
            if (done >= budget) break;

            BlockState state = mc.level.getBlockState(bp);
            if (state.canBeReplaced()) {
                pendingBlocks.remove(bp);
                continue;
            }

            float delta = state.getDestroyProgress(mc.player, mc.level, bp);
            if (rotate.getValue()) {
                rotateTo(bp);
            }

            Direction dir = getInteractDirection(bp);
            int seq = GrimUtils.nextSequence();

            if (delta >= 1.0f) {
                // Мгновенный майнинг (незерак при хорошей кирке)
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bp, dir, seq));
                    mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, bp, dir, seq));
                }
                mc.gameMode.destroyBlock(bp);
                pendingBlocks.remove(bp);
                done++;
            } else {
                // Блок требует нескольких тиков копания
                currentMiningPos = bp;
                currentMiningDamage = delta;
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bp, dir, seq));
                    mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
                }
                break;
            }
        }
    }

    protected Direction getInteractDirection(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 posVec = Vec3.atCenterOf(pos);
        Direction bestDir = Direction.UP;
        double bestDot = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            Vec3 dirVec = dir.getUnitVec3();
            double dot = eyePos.subtract(posVec).normalize().dot(dirVec);
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    protected void rotateTo(BlockPos bp) {
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(bp);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        RotationUtils.getInstance().setRotationSilentInstant(this, 50, yaw, pitch);
    }

    protected BlockPos flooredPlayerPos() {
        return new BlockPos(
            (int) Math.floor(mc.player.getX()),
            keepY.getIntValue() != -1 ? keepY.getIntValue() : (int) Math.floor(mc.player.getY()),
            (int) Math.floor(mc.player.getZ()));
    }
}