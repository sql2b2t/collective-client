package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.ColorUtil;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HoleESP extends Module {
    public static HoleESP INSTANCE;

    public enum Mode {
        CubeOutline, CubeFill, CubeBoth
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Режим", Mode.CubeBoth));
    public final NumberSetting rangeXZ = add(new NumberSetting("Радиус XY", 10.0, 3.0, 30.0, 1.0));
    public final NumberSetting rangeY = add(new NumberSetting("Радиус Y", 5.0, 1.0, 15.0, 1.0));
    public final NumberSetting height = add(new NumberSetting("Высота", 1.0, 0.1, 2.0, 0.1));

    public final ColorSetting bedrockColor = add(new ColorSetting("Бедрок", new Color(0, 255, 120, 100)));
    public final ColorSetting indestructibleColor = add(new ColorSetting("Обсидиан", new Color(140, 0, 255, 100)));
    public final BooleanSetting doubleHoles = add(new BooleanSetting("Двойные дыры", true));
    public final BooleanSetting xray = add(new BooleanSetting("Просвет", true));

    private final List<Hole> holes = new CopyOnWriteArrayList<>();
    private long lastScanTime = 0L;

    public HoleESP() {
        super("HoleESP", "Подсвечивает безопасные дыры из бедрока и обсидиана для кристал-пвп", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        holes.clear();
    }

    @Override
    public void onUpdate() {
        if (!isToggled() || mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScanTime >= 250L) {
            lastScanTime = now;
            scanHoles();
        }
    }

    private void scanHoles() {
        List<Hole> detected = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();
        int rX = rangeXZ.getIntValue();
        int rY = rangeY.getIntValue();

        for (int x = -rX; x <= rX; x++) {
            for (int y = -rY; y <= rY; y++) {
                for (int z = -rX; z <= rX; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Hole hole = checkHole(pos);
                    if (hole != null) {
                        detected.add(hole);
                    }
                }
            }
        }
        holes.clear();
        holes.addAll(detected);
    }

    private Hole checkHole(BlockPos pos) {
        if (isSolid(pos) || isSolid(pos.up()) || isSolid(pos.up(2))) return null;
        if (!isSafe(pos.down())) return null;

        boolean bedrock = mc.world.getBlockState(pos.down()).isOf(Blocks.BEDROCK);
        BlockPos secondPos = null;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos side = pos.offset(dir);
            BlockState sideState = mc.world.getBlockState(side);

            if (sideState.isOf(Blocks.BEDROCK)) {
                // Safe bedrock
            } else if (sideState.isOf(Blocks.OBSIDIAN) || sideState.isOf(Blocks.CRYING_OBSIDIAN) || sideState.isOf(Blocks.ANVIL)) {
                bedrock = false;
            } else if (doubleHoles.getValue() && !isSolid(side) && !isSolid(side.up())
                && !isSolid(side.up(2)) && isSafe(side.down()) && secondPos == null) {
                // Potential 2x1 hole
                secondPos = side;
            } else {
                return null;
            }
        }

        if (secondPos != null) {
            bedrock &= mc.world.getBlockState(secondPos.down()).isOf(Blocks.BEDROCK);
            // The shared edge is internal; every other side of the second cell
            // must be protected just like the sides of the first one.
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos side = secondPos.offset(dir);
                if (side.equals(pos)) continue;
                if (!isSafe(side)) return null;
                bedrock &= mc.world.getBlockState(side).isOf(Blocks.BEDROCK);
            }
        }

        double h = height.getValue();
        Color c = bedrock ? bedrockColor.getValue() : indestructibleColor.getValue();

        if (secondPos == null) {
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + h, pos.getZ() + 1.0);
            return new Hole(box, c);
        } else {
            double minX = Math.min(pos.getX(), secondPos.getX());
            double minZ = Math.min(pos.getZ(), secondPos.getZ());
            double maxX = Math.max(pos.getX(), secondPos.getX()) + 1.0;
            double maxZ = Math.max(pos.getZ(), secondPos.getZ()) + 1.0;
            Box box = new Box(minX, pos.getY(), minZ, maxX, pos.getY() + h, maxZ);
            return new Hole(box, c);
        }
    }

    private boolean isSafe(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN)
            || state.isOf(Blocks.CRYING_OBSIDIAN) || state.isOf(Blocks.ANVIL)
            || state.isOf(Blocks.RESPAWN_ANCHOR) || state.isOf(Blocks.NETHERITE_BLOCK);
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir()
                && !state.isOf(Blocks.FIRE)
                && !state.isOf(Blocks.SOUL_FIRE)
                && !(state.getBlock() instanceof ButtonBlock)
                && !(state.getBlock() instanceof TorchBlock)
                && !(state.getBlock() instanceof RailBlock)
                && !state.isOf(Blocks.LIGHT);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || holes.isEmpty() || mc.player == null || mc.world == null) return;

        ShapeMode sm = switch (mode.getValue()) {
            case CubeFill -> ShapeMode.Sides;
            case CubeOutline -> ShapeMode.Lines;
            case CubeBoth -> ShapeMode.Both;
        };

        double rangeBlocks = rangeXZ.getValue();
        for (Hole hole : holes) {
            // Мягкое затухание на дальней кромке радиуса вместо мгновенного
            // исчезновения — дыры не «поп»-ают при движении камеры.
            double dist = mc.player.squaredDistanceTo(hole.box.getCenter());
            if (dist > rangeBlocks * rangeBlocks) continue;
            dist = Math.sqrt(dist);
            Color base = hole.color;
            Color fill = ColorUtil.fadeByDistance(dist, rangeBlocks, base);
            Color line = ColorUtil.fadeLineByDistance(dist, rangeBlocks, base, 255);
            Renderer3D.box(hole.box, fill, line, sm, xray.getValue());
        }
    }

    public record Hole(Box box, Color color) {}
}
