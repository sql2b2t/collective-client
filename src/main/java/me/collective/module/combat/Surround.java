package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.GrimUtils;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import me.collective.util.SwapManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Surround extends Module {
    private static final int ROTATION_PRIORITY = 35;
    private static final Random RANDOM = new Random();

    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    public final BooleanSetting diagonal = add(new BooleanSetting("Diagonal", false));
    public final BooleanSetting doubleHeight = add(new BooleanSetting("DoubleHeight", false));
    public final BooleanSetting onlyOnGround = add(new BooleanSetting("OnlyOnGround", true));
    public final BooleanSetting center = add(new BooleanSetting("Center", true));
    public final NumberSetting blocksPerTick = add(new NumberSetting("BlocksPerTick", 2.0, 1.0, 5.0, 1.0));
    public final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 5.0, 1.0));

    private int delayTimer = 0;

    public Surround() {
        super("Surround", "Surrounds your feet with blast-resistant blocks to protect against crystals on GrimAC/2b2t.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        delayTimer = 0;
        if (mc.player != null && center.getValue() && mc.player.isOnGround()) {
            double cx = Math.floor(mc.player.getX()) + 0.5;
            double cz = Math.floor(mc.player.getZ()) + 0.5;
            mc.player.setPosition(cx, mc.player.getY(), cz);
        }
    }

    @Override
    public void onDisable() {
        RotationUtils.getInstance().release(this);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult obsidian = InventoryUtil.findInHotbar(Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK, Items.RESPAWN_ANCHOR);
        if (!obsidian.found()) {
            RotationUtils.getInstance().release(this);
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        List<BlockPos> targetPositions = new ArrayList<>();

        // Кардинальные стороны (N, S, E, W)
        targetPositions.add(playerPos.north());
        targetPositions.add(playerPos.south());
        targetPositions.add(playerPos.east());
        targetPositions.add(playerPos.west());

        // Диагональные позиции
        if (diagonal.getValue()) {
            targetPositions.add(playerPos.north().east());
            targetPositions.add(playerPos.north().west());
            targetPositions.add(playerPos.south().east());
            targetPositions.add(playerPos.south().west());
        }

        // Второй ярус (на уровне тела)
        if (doubleHeight.getValue()) {
            targetPositions.add(playerPos.north().up());
            targetPositions.add(playerPos.south().up());
            targetPositions.add(playerPos.east().up());
            targetPositions.add(playerPos.west().up());

            if (diagonal.getValue()) {
                targetPositions.add(playerPos.north().east().up());
                targetPositions.add(playerPos.north().west().up());
                targetPositions.add(playerPos.south().east().up());
                targetPositions.add(playerPos.south().west().up());
            }
        }

        // Сортируем: сначала те позиции, которые ближе к врагам или центру игрока
        Vec3d eyePos = mc.player.getEyePos();
        targetPositions.sort(Comparator.comparingDouble(p -> eyePos.squaredDistanceTo(p.toCenterPos())));

        int placed = 0;
        int maxPlaced = blocksPerTick.getValue().intValue();

        for (BlockPos pos : targetPositions) {
            if (placed >= maxPlaced) break;
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;

            // Не ставим, если хитбокс игрока пересекает блок
            Box blockBox = new Box(pos);
            if (mc.player.getBoundingBox().intersects(blockBox)) continue;

            if (placeBlock(pos, obsidian.slot())) {
                placed++;
            }
        }

        if (placed > 0) {
            delayTimer = delay.getValue().intValue();
        } else {
            RotationUtils.getInstance().release(this);
        }
    }

    private boolean placeBlock(BlockPos pos, int slot) {
        if (mc.player == null || mc.world == null) return false;

        // Поиск твёрдого соседа для клика (GrimAC требует валидную опору)
        Direction placeSide = null;
        BlockPos neighborPos = null;

        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.offset(side);
            BlockState state = mc.world.getBlockState(neighbor);
            if (!state.isReplaceable()) {
                placeSide = side.getOpposite();
                neighborPos = neighbor;
                break;
            }
        }

        Vec3d hitVec;
        BlockHitResult hitResult;

        if (neighborPos != null && placeSide != null) {
            // Небольшой случайный разброс точки клика на грани (обход эвристики GrimAC)
            double rx = (RANDOM.nextDouble() - 0.5) * 0.4;
            double ry = (RANDOM.nextDouble() - 0.5) * 0.4;
            double rz = (RANDOM.nextDouble() - 0.5) * 0.4;

            hitVec = new Vec3d(
                neighborPos.getX() + 0.5 + placeSide.getOffsetX() * 0.5 + (placeSide.getAxis() != Direction.Axis.X ? rx : 0),
                neighborPos.getY() + 0.5 + placeSide.getOffsetY() * 0.5 + (placeSide.getAxis() != Direction.Axis.Y ? ry : 0),
                neighborPos.getZ() + 0.5 + placeSide.getOffsetZ() * 0.5 + (placeSide.getAxis() != Direction.Axis.Z ? rz : 0)
            );
            hitResult = new BlockHitResult(hitVec, placeSide, neighborPos, false);
        } else {
            // Grim Air place fallback
            hitVec = Vec3d.ofCenter(pos);
            hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        }

        // Ротация под GrimAC
        if (rotate.getValue()) {
            float[] rots = RotationUtils.getRotationsTo(mc.player.getEyePos(), hitVec);
            RotationUtils.getInstance().setRotationSmooth(this, ROTATION_PRIORITY, rots[0], rots[1], 80.0, true);
            RotationUtils.getInstance().keepWireFresh();
        }

        // Безопасный свап через SwapManager
        if (!SwapManager.getInstance().hold(this, slot, SwapManager.Priority.PLACE, 2)) {
            return false;
        }

        if (neighborPos != null && placeSide != null) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.MAIN_HAND, hitResult, GrimUtils.nextSequence()
            ));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, BlockPos.ORIGIN, Direction.DOWN
            ));
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.MAIN_HAND, hitResult, GrimUtils.nextSequence()
            ));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, BlockPos.ORIGIN, Direction.DOWN
            ));
        }

        return true;
    }
}
