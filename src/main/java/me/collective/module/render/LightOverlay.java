package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.render.ColorUtil;
import me.collective.render.Renderer3D;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.IntegerSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * LightOverlay: порт модуля light-overlay из Meteor Client.
 * Показывает блоки (и верхний слой снега), где сейчас или потенциально
 * могут заспавниться мобы, крестиками на их верхних гранях.
 */
public class LightOverlay extends Module {
    public final IntegerSetting horizontalRange
        = add(new IntegerSetting("HorizontalRange", 8, 0, 24));
    public final IntegerSetting verticalRange
        = add(new IntegerSetting("VerticalRange", 4, 0, 8));
    public final IntegerSetting lightLevel
        = add(new IntegerSetting("LightLevel", 0, 0, 15));
    public final IntegerSetting updateInterval
        = add(new IntegerSetting("UpdateInterval", 4, 1, 20));
    public final BooleanSetting seeThroughBlocks = add(new BooleanSetting("SeeThroughBlocks", false));

    public final ColorSetting color
        = add(new ColorSetting("Color", new Color(255, 70, 82, 200)));
    public final ColorSetting potentialColor
        = add(new ColorSetting("PotentialColor", new Color(255, 214, 72, 160)));

    private final java.util.List<Cross> crosses = new java.util.ArrayList<>();
    private int scanTicks;

    public LightOverlay() {
        super("LightOverlay", "Показывает блоки, где могут заспавниться мобы.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        this.crosses.clear();
        this.scanTicks = 0;
    }

    @Override
    public void onDisable() {
        this.crosses.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Мигание было из-за безусловного clear() каждый тик: между
        // сканами список оказывался пуст и крестики пропадали на кадры.
        // Теперь скан строится во временный список, а swap происходит
        // атомарно в конце — между обновлениями отрисовка стабильна.
        if (++this.scanTicks < this.updateInterval.getIntValue()) return;
        this.scanTicks = 0;

        List<Cross> rebuilt = new ArrayList<>();
        this.scanCrosses(rebuilt);
        this.crosses.clear();
        this.crosses.addAll(rebuilt);
    }

    private void scanCrosses(List<Cross> out) {
        int h = this.horizontalRange.getIntValue();
        int v = this.verticalRange.getIntValue();
        int limit = this.lightLevel.getIntValue();

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int baseX = mc.player.getBlockPos().getX();
        int baseY = mc.player.getBlockPos().getY();
        int baseZ = mc.player.getBlockPos().getZ();

        for (int dy = -v; dy <= v; dy++) {
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    cursor.set(baseX + dx, baseY + dy, baseZ + dz);

                    if (!mc.world.isChunkLoaded(cursor.getX() >> 4, cursor.getZ() >> 4)) continue;

                    switch (isValidMobSpawn(cursor.toImmutable(), limit)) {
                        case Potential -> out.add(new Cross(cursor, true));
                        case Always -> out.add(new Cross(cursor, false));
                        case Never -> { }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || this.crosses.isEmpty()) return;

        Color cSpawn = this.color.getValue();
        Color cPotential = this.potentialColor.getValue();
        // Крестики у краёв радиуса плавно тают — крайняя кромка не «режет глаз».
        double rangeBlocks = horizontalRange.getValue();

        for (Cross cross : this.crosses) {
            Color base = cross.potential ? cPotential : cSpawn;
            double dist = mc.player.squaredDistanceTo(
                cross.x + 0.5, cross.y, cross.z + 0.5);
            dist = Math.sqrt(dist);
            Color c = ColorUtil.fadeLineByDistance(dist, rangeBlocks, base, base.getAlpha());

            drawLine(cross.x, cross.y, cross.z, cross.x + 1, cross.y, cross.z + 1, c);
            drawLine(cross.x + 1, cross.y, cross.z, cross.x, cross.y, cross.z + 1, c);
        }
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color c) {
        if (this.seeThroughBlocks.getValue()) {
            if (mc.worldRenderer == null) return;
            try (var scope = mc.worldRenderer.startDrawingGizmos()) {
                int rgba = (c.getAlpha() & 0xFF) << 24 | (c.getRed() & 0xFF) << 16
                    | (c.getGreen() & 0xFF) << 8 | (c.getBlue() & 0xFF);
                GizmoDrawing.line(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), rgba)
                    .ignoreOcclusion();
            } catch (Exception ignored) {
            }
        } else {
            Renderer3D.line(x1, y1, z1, x2, y2, z2, c);
        }
    }

    /**
     * Порт Meteor BlockUtils.isValidMobSpawn: воздух (или одиночный слой
     * снега) на валидной опоре; block-light не выше порога; если sky-light
     * выше порога — спавн только ночью (Potential), иначе — всегда (Always).
     */
    public Spawn isValidMobSpawn(BlockPos pos, int spawnLightLimit) {
        net.minecraft.block.BlockState state = mc.world.getBlockState(pos);

        boolean snow = state.contains(SnowBlock.LAYERS) && state.get(SnowBlock.LAYERS) == 1;
        if (!state.isAir() && !snow) return Spawn.Never;

        if (!isValidSpawnBlock(pos.down())) return Spawn.Never;

        if (getLight(LightType.BLOCK, pos) > spawnLightLimit) return Spawn.Never;
        if (getLight(LightType.SKY, pos) > spawnLightLimit) return Spawn.Potential;

        return Spawn.Always;
    }

    /** Порт Meteor BlockUtils.isValidSpawnBlock под Yarn 1.21.11. */
    public boolean isValidSpawnBlock(BlockPos pos) {
        net.minecraft.block.BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.BEDROCK
            || block == Blocks.BARRIER
            || block instanceof TransparentBlock
            || block instanceof ScaffoldingBlock) return false;

        if (block == Blocks.SOUL_SAND || block == Blocks.MUD) return true;
        if (block instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.TOP) return true;
        if (block instanceof StairsBlock && state.get(StairsBlock.HALF) == BlockHalf.TOP) return true;

        return Block.isShapeFullCube(state.getCollisionShape(mc.world, pos));
    }

    private int getLight(LightType type, BlockPos pos) {
        return mc.world.getLightingProvider().get(type).getLightLevel(pos);
    }

    private static final class Cross {
        final double x;
        final double y;
        final double z;
        final boolean potential;

        Cross(BlockPos pos, boolean potential) {
            this.x = pos.getX();
            this.y = pos.getY() + 0.0075;
            this.z = pos.getZ();
            this.potential = potential;
        }
    }

    public enum Spawn {
        Never,
        Potential,
        Always
    }
}
