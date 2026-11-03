package me.collective.module.player;

import me.collective.event.events.Render3DEvent;
import me.collective.render.ShapeMode;
import me.collective.util.FadeAnimator;
import me.collective.util.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Отрисовка прогресса копания для {@link SpeedMine}: затухающие боксы
 * активных, вторичных и стоящих в очереди целей с заполнением по прогрессу.
 *
 * <p>Выделено из тела {@link SpeedMine} по паттерну компаньонов: модуль
 * держит тонкий делегат, рендер-состояние ({@code fadeList}/{@code FadeEntry})
 * живёт здесь.
 */
final class SpeedMineRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<BlockPos, FadeEntry> fadeList = new HashMap<>();

    private static final class FadeEntry {
        private SpeedMine.MiningData data;
        private final FadeAnimator fade = new FadeAnimator();

        private FadeEntry(SpeedMine.MiningData data) {
            this.data = data;
        }
    }

    /** Сброс всех затухающих боксов (отключение модуля / смена мира). */
    void clear() {
        fadeList.clear();
    }

    void onRenderWorld(SpeedMine b, Render3DEvent event) {
        if (mc.player != null && mc.world != null) {
            if (!mc.player.isCreative() && b.modeConfig.get() == SpeedMine.SpeedmineMode.PACKET && b.render.get()) {
                trackFade(b.activeMine);
                trackFade(b.secondaryMine);

                for (SpeedMine.MiningData data : b.pending) {
                    trackFade(data);
                }

                float total = b.getEffectiveThreshold();
                double fadeSeconds = b.fadeTimeConfig.get().intValue() / 1000.0;
                SettingColor mineColor = b.colorConfig.get();
                SettingColor doneColor = b.colorDoneConfig.get();
                Iterator<FadeEntry> it = fadeList.values().iterator();

                while (it.hasNext()) {
                    FadeEntry entry = it.next();
                    SpeedMine.MiningData data = entry.data;
                    BlockState state = data.getState();
                    boolean air = state.isAir();
                    boolean primary = data == b.activeMine || data == b.secondaryMine;
                    boolean queued = !primary && b.pending.contains(data);
                    boolean visible = (primary || queued) && !air;
                    entry.fade.update(visible, fadeSeconds, true);
                    if (!visible && !entry.fade.rendering()) {
                        it.remove();
                    } else {
                        float factor = entry.fade.alpha();
                        int boxAlpha = (int) ((queued ? 20 : 40) * factor);
                        int lineAlpha = (int) ((queued ? 60 : 100) * factor);
                        boolean done = !queued && (data.damage >= total || air);
                        SettingColor base = done ? doneColor : mineColor;
                        b.renderBoxColor.set(base.r, base.g, base.b, boxAlpha);
                        b.renderLineColor.set(base.r, base.g, base.b, lineAlpha);
                        BlockPos mining = data.pos;
                        VoxelShape outlineShape = state.getOutlineShape(mc.world, mining);
                        Box bounds = (outlineShape.isEmpty() ? VoxelShapes.fullCube() : outlineShape).getBoundingBox();
                        float scale = !queued && !air
                            ? MathHelper.clamp((data.damage + (data.damage - data.lastDamage) * event.tickDelta) / total, 0.0F, 1.0F)
                            : 1.0F;
                        double cx = mining.getX() + (bounds.minX + bounds.maxX) / 2.0;
                        double cy = mining.getY() + (bounds.minY + bounds.maxY) / 2.0;
                        double cz = mining.getZ() + (bounds.minZ + bounds.maxZ) / 2.0;
                        double hx = (bounds.maxX - bounds.minX) / 2.0 * scale;
                        double hy = (bounds.maxY - bounds.minY) / 2.0 * scale;
                        double hz = (bounds.maxZ - bounds.minZ) / 2.0 * scale;
                        event.renderer.box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, b.renderBoxColor, b.renderLineColor, b.shapeMode.get(), 0);
                    }
                }
            }
        }
    }

    private void trackFade(SpeedMine.MiningData data) {
        if (data != null && !data.getState().isAir()) {
            FadeEntry existing = fadeList.get(data.pos);
            if (existing == null) {
                fadeList.put(data.pos, new FadeEntry(data));
            } else {
                existing.data = data;
            }
        }
    }
}
