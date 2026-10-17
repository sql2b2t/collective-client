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
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
        super("LiquidFillerHIG", "Places blocks inside of liquid source blocks within range of you.", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

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
            item = InventoryUtil.findInHotbar(stack ->
                stack.getItem() instanceof BlockItem && filter.contains(Block.getBlockFromItem(stack.getItem())));
        } else {
            item = InventoryUtil.findInHotbar(stack ->
                stack.getItem() instanceof BlockItem && !filter.contains(Block.getBlockFromItem(stack.getItem())));
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

                    Fluid fluid = mc.world.getBlockState(pos).getFluidState().getFluid();
                    if ((placeInLiquids.getValue() == PlaceIn.Both && (fluid != Fluids.WATER && fluid != Fluids.LAVA))
                        || (placeInLiquids.getValue() == PlaceIn.Water && fluid != Fluids.WATER)
                        || (placeInLiquids.getValue() == PlaceIn.Lava && fluid != Fluids.LAVA)) continue;

                    if (!mc.world.getBlockState(pos).isReplaceable()) continue;

                    targets.add(pos.toImmutable());
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
            if (place(pos, item.slot())) count++;
        }
        targets.clear();
    }

    /** Ставит блок с опорой на соседний цельный блок. False если опереться не на что. */
    private boolean place(BlockPos pos, int hotbarSlot) {
        Direction[] order = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : order) {
            BlockPos neighbor = pos.offset(dir);
            if (!mc.world.getBlockState(neighbor).isReplaceable()
                && mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) {
                Direction side = dir.getOpposite();
                Vec3d hitVec = Vec3d.ofCenter(neighbor).add(
                    side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

                InventoryUtil.swap(hotbarSlot, true);
                if (rotate.getValue()) {
                    Vec3d eyes = mc.player.getEyePos();
                    double dx = hitVec.x - eyes.x;
                    double dy = hitVec.y - eyes.y;
                    double dz = hitVec.z - eyes.z;
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
                    RotationUtils.getInstance().setRotationSilentInstant(this, 40, yaw, pitch);
                }
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(hitVec, side, neighbor, false));
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                }
                InventoryUtil.swapBack();
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
            if (ident == null || !Registries.BLOCK.containsId(ident)) continue;
            out.add(Registries.BLOCK.get(ident));
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
