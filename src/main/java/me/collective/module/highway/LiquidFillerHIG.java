package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BlockListSetting;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.StringSetting;
import me.collective.util.FindItemResult;
import me.collective.util.GrimUtils;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Порт LiquidFillerHIG: закладывает блоками источники жидкости рядом.
 * Список блоков — через запятую.
 */
public class LiquidFillerHIG extends Module {
    public enum PlaceIn {
        Both,
        Water,
        Lava
    }

    public enum SortMode {
        None,
        Closest,
        Furthest,
        TopDown,
        BottomUp
    }

    public enum Shape {
        Sphere,
        UniformCube
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }

    public final EnumSetting<PlaceIn> placeInLiquids = add(new EnumSetting<>("PlaceIn", PlaceIn.Lava));
    public final EnumSetting<Shape> shape = add(new EnumSetting<>("Shape", Shape.UniformCube));
    public final NumberSetting range = add(new NumberSetting("Range", 4.4, 0.0, 10.0, 0.1));
    public final NumberSetting delay = add(new NumberSetting("Delay", 0, 0, 20, 1));
    public final NumberSetting maxBlocksPerTick = add(new NumberSetting("MaxPerTick", 1, 1, 10, 1));
    public final EnumSetting<SortMode> sortMode = add(new EnumSetting<>("SortMode", SortMode.Closest));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    public final EnumSetting<ListMode> listMode = add(new EnumSetting<>("ListMode", ListMode.Whitelist));
    public final StringSetting blocks = add(new StringSetting("Blocks", "netherrack"));

    private final List<BlockPos> targets = new ArrayList<>();
    private int timer;

    public LiquidFillerHIG() {
        super("LiquidFillerHIG", "Заливает источники лавы и воды блоками в радиусе действия.", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (timer < delay.getIntValue()) {
            timer++;
            return;
        }
        timer = 0;

        double pX = mc.player.getX();
        double pY = mc.player.getY();
        double pZ = mc.player.getZ();
        double r = shape.getValue() == Shape.UniformCube ? Math.round(range.getValue()) : range.getValue();
        double rangeSq = r * r;

        FindItemResult item;
        Set<Block> filter = parseBlocks();
        if (listMode.getValue() == ListMode.Whitelist) {
            item = InventoryUtil.findInHotbarOrOffhand(stack ->
                stack.getItem() instanceof BlockItem && filter.contains(Block.byItem(stack.getItem())));
        } else {
            item = InventoryUtil.findInHotbarOrOffhand(stack ->
                stack.getItem() instanceof BlockItem && !filter.contains(Block.byItem(stack.getItem())));
        }
        if (!item.found()) return;

        targets.clear();
        int hr = (int) Math.ceil(r + 1);
        int vr = (int) Math.ceil(r);
        int baseX = (int) Math.floor(pX);
        int baseY = (int) Math.floor(pY);
        int baseZ = (int) Math.floor(pZ);
        for (int dx = -hr; dx <= hr; dx++) {
            for (int dy = -vr; dy <= vr; dy++) {
                for (int dz = -hr; dz <= hr; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    boolean tooFarSphere = squaredDist(pX, pY, pZ, pos) > rangeSq;
                    boolean tooFarCube = maxDist(Math.floor(pX), Math.floor(pY), Math.floor(pZ),
                        pos.getX(), pos.getY(), pos.getZ()) >= r;
                    boolean belowPlayer = pos.getY() + 0.5 < mc.player.getY() - 1;
                    if ((tooFarSphere && shape.getValue() == Shape.Sphere)
                        || (tooFarCube && shape.getValue() == Shape.UniformCube)
                        || belowPlayer) continue;

                    Fluid fluid = mc.level.getBlockState(pos).getFluidState().getType();
                    if ((placeInLiquids.getValue() == PlaceIn.Both && (fluid != Fluids.WATER && fluid != Fluids.LAVA))
                        || (placeInLiquids.getValue() == PlaceIn.Water && fluid != Fluids.WATER)
                        || (placeInLiquids.getValue() == PlaceIn.Lava && fluid != Fluids.LAVA)) continue;

                    if (!mc.level.getBlockState(pos).canBeReplaced()) continue;

                    targets.add(pos.immutable());
                }
            }
        }

        if (sortMode.getValue() == SortMode.TopDown || sortMode.getValue() == SortMode.BottomUp) {
            boolean up = sortMode.getValue() == SortMode.BottomUp;
            targets.sort(Comparator.comparingDouble((BlockPos v) -> v.getY() * (up ? 1 : -1)));
        } else if (sortMode.getValue() != SortMode.None) {
            boolean closest = sortMode.getValue() == SortMode.Closest;
            targets.sort(Comparator.comparingDouble((BlockPos v) ->
                squaredDist(pX, pY, pZ, v) * (closest ? 1 : -1)));
        }

        int count = 0;
        for (BlockPos pos : targets) {
            if (count >= maxBlocksPerTick.getIntValue()) break;
            if (place(pos, item)) count++;
        }
        targets.clear();
    }

    /** Ставит блок с опорой на соседний цельный блок. False если опереться не на что. */
    private boolean place(BlockPos pos, FindItemResult item) {
        Direction[] order = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : order) {
            BlockPos neighbor = pos.relative(dir);
            if (!mc.level.getBlockState(neighbor).canBeReplaced()
                && mc.level.getBlockState(neighbor).isSolid()) {
                Direction side = dir.getOpposite();
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                    side.getStepX() * 0.45, side.getStepY() * 0.45, side.getStepZ() * 0.45);

                if (GrimUtils.closestEyeDistanceSqTo(mc.player, hitVec) > 18.0) continue;

                InteractionHand hand = item.getHand();
                if (!item.isOffhand()) {
                    InventoryUtil.swap(item.slot(), true);
                }

                if (rotate.getValue()) {
                    Vec3 eyes = mc.player.getEyePosition();
                    double dx = hitVec.x - eyes.x;
                    double dy = hitVec.y - eyes.y;
                    double dz = hitVec.z - eyes.z;
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
                    RotationUtils.getInstance().setRotationSilentInstant(this, 40, yaw, pitch);
                }
                mc.gameMode.useItemOn(mc.player, hand,
                    new BlockHitResult(hitVec, side, neighbor, false));
                if (mc.getConnection() != null) {
                    mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
                }
                if (!item.isOffhand()) {
                    InventoryUtil.swapBack();
                }
                return true;
            }
        }
        return false;
    }

    private Set<Block> parseBlocks() {
        Set<Block> out = new HashSet<>();
        for (String raw : blocks.getValue().split(",")) {
            String id = raw.strip().toLowerCase();
            if (id.isEmpty()) continue;
            if (!id.contains(":")) id = "minecraft:" + id;
            Identifier ident = Identifier.tryParse(id);
            if (ident == null || !BuiltInRegistries.BLOCK.containsKey(ident)) continue;
            out.add(BuiltInRegistries.BLOCK.getValue(ident));
        }
        return out;
    }

    private static double squaredDist(double x1, double y1, double z1, BlockPos pos) {
        return squaredDist(x1, y1, z1, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static double squaredDist(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double maxDist(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dX = Math.ceil(Math.abs(x2 - x1));
        double dY = Math.ceil(Math.abs(y2 - y1));
        double dZ = Math.ceil(Math.abs(z2 - z1));
        return Math.max(Math.max(dX, dY), dZ);
    }
}