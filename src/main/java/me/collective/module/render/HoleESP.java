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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

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
        if (!isToggled() || mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScanTime >= 250L) {
            lastScanTime = now;
            scanHoles();
        }
    }

    private void scanHoles() {
        List<Hole> detected = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        int rX = rangeXZ.getIntValue();
        int rY = rangeY.getIntValue();

        for (int x = -rX; x <= rX; x++) {
            for (int y = -rY; y <= rY; y++) {
                for (int z = -rX; z <= rX; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
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
        if (isSolid(pos) || isSolid(pos.above()) || isSolid(pos.above(2))) return null;
        if (!isSafe(pos.below())) return null;

        boolean bedrock = mc.level.getBlockState(pos.below()).is(Blocks.BEDROCK);
        BlockPos secondPos = null;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            BlockState sideState = mc.level.getBlockState(side);

            if (sideState.is(Blocks.BEDROCK)) {
                // Safe bedrock
            } else if (sideState.is(Blocks.OBSIDIAN) || sideState.is(Blocks.CRYING_OBSIDIAN) || sideState.is(Blocks.ANVIL)) {
                bedrock = false;
            } else if (doubleHoles.getValue() && !isSolid(side) && !isSolid(side.above())
                && !isSolid(side.above(2)) && isSafe(side.below()) && secondPos == null) {
                // Potential 2x1 hole
                secondPos = side;
            } else {
                return null;
            }
        }

        if (secondPos != null) {
            bedrock &= mc.level.getBlockState(secondPos.below()).is(Blocks.BEDROCK);
            // The shared edge is internal; every other side of the second cell
            // must be protected just like the sides of the first one.
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = secondPos.relative(dir);
                if (side.equals(pos)) continue;
                if (!isSafe(side)) return null;
                bedrock &= mc.level.getBlockState(side).is(Blocks.BEDROCK);
            }
        }

        double h = height.getValue();
        Color c = bedrock ? bedrockColor.getValue() : indestructibleColor.getValue();

        if (secondPos == null) {
            AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + h, pos.getZ() + 1.0);
            return new Hole(box, c);
        } else {
            double minX = Math.min(pos.getX(), secondPos.getX());
            double minZ = Math.min(pos.getZ(), secondPos.getZ());
            double maxX = Math.max(pos.getX(), secondPos.getX()) + 1.0;
            double maxZ = Math.max(pos.getZ(), secondPos.getZ()) + 1.0;
            AABB box = new AABB(minX, pos.getY(), minZ, maxX, pos.getY() + h, maxZ);
            return new Hole(box, c);
        }
    }

    private boolean isSafe(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.ANVIL)
            || state.is(Blocks.RESPAWN_ANCHOR) || state.is(Blocks.NETHERITE_BLOCK);
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir()
                && !state.is(Blocks.FIRE)
                && !state.is(Blocks.SOUL_FIRE)
                && !(state.getBlock() instanceof ButtonBlock)
                && !(state.getBlock() instanceof TorchBlock)
                && !(state.getBlock() instanceof RailBlock)
                && !state.is(Blocks.LIGHT);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || holes.isEmpty() || mc.player == null || mc.level == null) return;

        ShapeMode sm = switch (mode.getValue()) {
            case CubeFill -> ShapeMode.Sides;
            case CubeOutline -> ShapeMode.Lines;
            case CubeBoth -> ShapeMode.Both;
        };

        double rangeBlocks = rangeXZ.getValue();
        for (Hole hole : holes) {
            // Мягкое затухание на дальней кромке радиуса вместо мгновенного
            // исчезновения — дыры не «поп»-ают при движении камеры.
            double dist = mc.player.distanceToSqr(hole.box.getCenter());
            if (dist > rangeBlocks * rangeBlocks) continue;
            dist = Math.sqrt(dist);
            Color base = hole.color;
            Color fill = ColorUtil.fadeByDistance(dist, rangeBlocks, base);
            Color line = ColorUtil.fadeLineByDistance(dist, rangeBlocks, base, 255);
            Renderer3D.box(hole.box, fill, line, sm, xray.getValue());
        }
    }

    public record Hole(AABB box, Color color) {}
}