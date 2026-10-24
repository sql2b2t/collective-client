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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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
        super("Surround", "Окружает ноги взрывоустойчивыми блоками для защиты от кристаллов.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        delayTimer = 0;
        if (mc.player != null && center.getValue() && mc.player.onGround()) {
            double cx = Math.floor(mc.player.getX()) + 0.5;
            double cz = Math.floor(mc.player.getZ()) + 0.5;
            mc.player.setPos(cx, mc.player.getY(), cz);
        }
    }

    @Override
    public void onDisable() {
        RotationUtils.getInstance().release(this);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (onlyOnGround.getValue() && !mc.player.onGround()) return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult obsidian = InventoryUtil.findInHotbarOrOffhand(Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK, Items.RESPAWN_ANCHOR);
        if (!obsidian.found()) {
            RotationUtils.getInstance().release(this);
            return;
        }

        // Ярусные цели: сначала полностью закрываем первый ярус (у ног),
        // только потом тратим обсидиан на второй — иначе над дыркой
        // появится крыша, а стенки так и останутся с дырой.
        BlockPos playerPos = mc.player.blockPosition();
        List<BlockPos> tier0 = new ArrayList<>();
        List<BlockPos> tier1 = new ArrayList<>();

        tier0.add(playerPos.north());
        tier0.add(playerPos.south());
        tier0.add(playerPos.east());
        tier0.add(playerPos.west());

        if (diagonal.getValue()) {
            tier0.add(playerPos.north().east());
            tier0.add(playerPos.north().west());
            tier0.add(playerPos.south().east());
            tier0.add(playerPos.south().west());
        }

        if (doubleHeight.getValue()) {
            tier1.add(playerPos.north().above());
            tier1.add(playerPos.south().above());
            tier1.add(playerPos.east().above());
            tier1.add(playerPos.west().above());

            if (diagonal.getValue()) {
                tier1.add(playerPos.north().east().above());
                tier1.add(playerPos.north().west().above());
                tier1.add(playerPos.south().east().above());
                tier1.add(playerPos.south().west().above());
            }
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Comparator<BlockPos> byDistance
            = Comparator.comparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
        tier0.sort(byDistance);
        tier1.sort(byDistance);

        List<BlockPos> targetPositions = new ArrayList<>(tier0);
        targetPositions.addAll(tier1);

        int placed = 0;
        int maxPlaced = blocksPerTick.getValue().intValue();

        for (BlockPos pos : targetPositions) {
            if (placed >= maxPlaced) break;
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;

            // Не ставим, если хитбокс игрока пересекает блок
            AABB blockBox = new AABB(pos).deflate(0.01);
            if (mc.player.getBoundingBox().intersects(blockBox)) continue;

            if (placeBlock(pos, obsidian)) {
                placed++;
            }
        }

        if (placed > 0) {
            delayTimer = delay.getValue().intValue();
        } else {
            RotationUtils.getInstance().release(this);
        }
    }

    private boolean placeBlock(BlockPos pos, FindItemResult obsidian) {
        if (mc.player == null || mc.level == null) return false;

        // Поиск твёрдого соседа для клика (GrimAC требует валидную опору)
        Direction placeSide = null;
        BlockPos neighborPos = null;

        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            BlockState state = mc.level.getBlockState(neighbor);
            if (!state.canBeReplaced()) {
                placeSide = side.getOpposite();
                neighborPos = neighbor;
                break;
            }
        }

        Vec3 hitVec;
        BlockHitResult hitResult;

        if (neighborPos != null && placeSide != null) {
            // Небольшой случайный разброс точки клика на грани (обход эвристики GrimAC)
            double rx = (RANDOM.nextDouble() - 0.5) * 0.4;
            double ry = (RANDOM.nextDouble() - 0.5) * 0.4;
            double rz = (RANDOM.nextDouble() - 0.5) * 0.4;

            hitVec = new Vec3(
                neighborPos.getX() + 0.5 + placeSide.getStepX() * 0.5 + (placeSide.getAxis() != Direction.Axis.X ? rx : 0),
                neighborPos.getY() + 0.5 + placeSide.getStepY() * 0.5 + (placeSide.getAxis() != Direction.Axis.Y ? ry : 0),
                neighborPos.getZ() + 0.5 + placeSide.getStepZ() * 0.5 + (placeSide.getAxis() != Direction.Axis.Z ? rz : 0)
            );
            hitResult = new BlockHitResult(hitVec, placeSide, neighborPos, false);
        } else {
            // Grim Air place fallback
            hitVec = Vec3.atCenterOf(pos);
            hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        }

        // Ротация под GrimAC
        if (rotate.getValue()) {
            float[] rots = RotationUtils.getRotationsTo(mc.player.getEyePosition(), hitVec);
            RotationUtils.getInstance().setRotationSmooth(this, ROTATION_PRIORITY, rots[0], rots[1], 80.0, true);
            RotationUtils.getInstance().keepWireFresh();
        }

        InteractionHand hand = obsidian.getHand();
        if (!obsidian.isOffhand()) {
            if (!SwapManager.getInstance().hold(this, obsidian.slot(), SwapManager.Priority.PLACE, 2)) {
                return false;
            }
        }

        if (neighborPos != null && placeSide != null) {
            mc.getConnection().send(new ServerboundUseItemOnPacket(
                hand, hitResult, GrimUtils.nextSequence()
            ));
            mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
        } else {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, BlockPos.ZERO, Direction.DOWN
            ));
            mc.getConnection().send(new ServerboundUseItemOnPacket(
                hand, hitResult, GrimUtils.nextSequence()
            ));
            mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, BlockPos.ZERO, Direction.DOWN
            ));
        }

        return true;
    }
}