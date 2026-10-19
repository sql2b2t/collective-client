package me.collective.render;

import me.collective.util.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.awt.Color;

public class Renderer3D {
    /**
     * Shared per-frame gizmo scope: scopes in the collective opening {@link #beginFrameScope()},
     * box()/line() calls directly into the general collector, without their own try-with-resources
     * for each box. Outside of the frame — each call works on its own scope, as before.
     */
    private static GizmoDrawing.CollectorScope frameScope;

    /** General instance for the {@code event.renderer} field — without allocating a new object every frame. */
    public static final Renderer3D SHARED = new Renderer3D();

    public boolean open() {
        return frameScope != null;
    }

    public static void beginFrameScope() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer == null) return;
        try {
            frameScope = mc.worldRenderer.startDrawingGizmos();
        } catch (Throwable t) {
            frameScope = null;
        }
    }

    public static void endFrameScope() {
        GizmoDrawing.CollectorScope scope = frameScope;
        frameScope = null;
        if (scope == null) return;
        try {
            scope.close();
        } catch (Exception ignored) {
        }
    }

    private static int rgba(Color c) {
        return (c.getAlpha() & 0xFF) << 24 | (c.getRed() & 0xFF) << 16
            | (c.getGreen() & 0xFF) << 8 | (c.getBlue() & 0xFF);
    }

    private static Color toAwt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Color c) return c;
        if (obj instanceof SettingColor sc) return sc.toAwt();
        return null;
    }

    public static void box(double x1, double y1, double z1, double x2, double y2, double z2, Color fill, Color line, ShapeMode shapeMode) {
        box(x1, y1, z1, x2, y2, z2, fill, line, shapeMode, false);
    }

    public static void box(double x1, double y1, double z1, double x2, double y2, double z2, Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion) {
        if (frameScope != null) {
            // Общий скоуп кадра уже открыт — рисуем напрямую, без своего на каждый бокс.
            drawBoxRaw(x1, y1, z1, x2, y2, z2, fill, line, shapeMode, ignoreOcclusion);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer == null) return;
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            drawBoxRaw(x1, y1, z1, x2, y2, z2, fill, line, shapeMode, ignoreOcclusion);
        } catch (Exception ignored) {
        }
    }

    private static void drawBoxRaw(double x1, double y1, double z1, double x2, double y2, double z2,
                                   Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion) {
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        double maxZ = Math.max(z1, z2);
        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);

        int fillRgba = fill != null ? rgba(fill) : 0;
        int lineRgba = line != null ? rgba(line) : 0;

        if (shapeMode == ShapeMode.Both && fill != null && line != null) {
            var vis = GizmoDrawing.box(box, DrawStyle.filledAndStroked(fillRgba, 1.5f, lineRgba));
            if (ignoreOcclusion) vis.ignoreOcclusion();
        } else if ((shapeMode == ShapeMode.Sides || shapeMode == ShapeMode.Both) && fill != null) {
            var vis = GizmoDrawing.box(box, DrawStyle.filled(fillRgba));
            if (ignoreOcclusion) vis.ignoreOcclusion();
        } else if ((shapeMode == ShapeMode.Lines || shapeMode == ShapeMode.Both) && line != null) {
            var vis = GizmoDrawing.box(box, DrawStyle.stroked(lineRgba, 1.5f));
            if (ignoreOcclusion) vis.ignoreOcclusion();
        }
    }

    public static void box(double x1, double y1, double z1, double x2, double y2, double z2, Object fill, Object line, ShapeMode shapeMode, int excludeDirs) {
        box(x1, y1, z1, x2, y2, z2, toAwt(fill), toAwt(line), shapeMode != null ? shapeMode : ShapeMode.Both);
    }

    public static void box(net.minecraft.util.math.BlockPos pos, Object fill, Object line, ShapeMode shapeMode, int excludeDirs) {
        if (pos == null) return;
        box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, fill, line, shapeMode, excludeDirs);
    }

    public static void box(double x1, double y1, double z1, double x2, double y2, double z2, Color fill, Color line) {
        box(x1, y1, z1, x2, y2, z2, fill, line, ShapeMode.Both);
    }

    public static void box(Box box, Color fill, Color line) {
        if (box == null) return;
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, ShapeMode.Both);
    }

    public static void box(Box box, Color fill, Color line, ShapeMode shapeMode) {
        if (box == null) return;
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, shapeMode, false);
    }

    public static void box(Box box, Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion) {
        if (box == null) return;
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, shapeMode, ignoreOcclusion);
    }

    public static void line(double x1, double y1, double z1, double x2, double y2, double z2, Object color) {
        Color c = toAwt(color);
        if (c == null) return;

        if (frameScope != null) {
            // Общий скоуп кадра — без собственного try-with-resources на каждую линию.
            GizmoDrawing.line(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), rgba(c), 1.5f);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer == null) return;
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            GizmoDrawing.line(new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), rgba(c), 1.5f);
        } catch (Exception ignored) {
        }
    }
}
