package me.collective.render;

import me.collective.util.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class Renderer3D {
    /**
     * Shared per-frame gizmo scope: scopes in the collective opening {@link #beginFrameScope()},
     * box()/line() calls directly into the general collector, without their own try-with-resources
     * for each box. Outside of the frame — each call works on its own scope, as before.
     */
    private static Gizmos.TemporaryCollection frameScope;

    /** General instance for the {@code event.renderer} field — without allocating a new object every frame. */
    public static final Renderer3D SHARED = new Renderer3D();

    public boolean open() {
        return frameScope != null;
    }

    public static void beginFrameScope() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;
        try {
            frameScope = mc.levelRenderer.collectPerFrameRenderThreadGizmos();
        } catch (Throwable t) {
            frameScope = null;
        }
    }

    public static void endFrameScope() {
        Gizmos.TemporaryCollection scope = frameScope;
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
            drawBoxRaw(x1, y1, z1, x2, y2, z2, fill, line, shapeMode, ignoreOcclusion);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;
        try (var scope = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            drawBoxRaw(x1, y1, z1, x2, y2, z2, fill, line, shapeMode, ignoreOcclusion);
        } catch (Exception ignored) {
        }
    }

    private static void drawBoxRaw(double x1, double y1, double z1, double x2, double y2, double z2,
                                   Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion) {
        drawBoxRaw(x1, y1, z1, x2, y2, z2, fill, line, shapeMode, ignoreOcclusion, 1.5f);
    }

    private static void drawBoxRaw(double x1, double y1, double z1, double x2, double y2, double z2,
                                   Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion, float lineWidth) {
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        double maxZ = Math.max(z1, z2);
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        int fillRgba = fill != null ? rgba(fill) : 0;
        int lineRgba = line != null ? rgba(line) : 0;
        float lw = Math.max(0.1f, lineWidth);

        GizmoStyle style;
        if (shapeMode == ShapeMode.Both && fill != null && line != null) {
            style = GizmoStyle.strokeAndFill(lineRgba, lw, fillRgba);
        } else if ((shapeMode == ShapeMode.Sides || shapeMode == ShapeMode.Both) && fill != null) {
            style = GizmoStyle.fill(fillRgba);
        } else if ((shapeMode == ShapeMode.Lines || shapeMode == ShapeMode.Both) && line != null) {
            style = GizmoStyle.stroke(lineRgba, lw);
        } else {
            return;
        }

        var vis = Gizmos.cuboid(box, style);
        if (ignoreOcclusion) vis.setAlwaysOnTop();
    }

    public static void box(double x1, double y1, double z1, double x2, double y2, double z2, Object fill, Object line, ShapeMode shapeMode, int excludeDirs) {
        box(x1, y1, z1, x2, y2, z2, toAwt(fill), toAwt(line), shapeMode != null ? shapeMode : ShapeMode.Both);
    }

    public static void box(BlockPos pos, Object fill, Object line, ShapeMode shapeMode, int excludeDirs) {
        if (pos == null) return;
        box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, fill, line, shapeMode, excludeDirs);
    }

    public static void box(double x1, double y1, double z1, double x2, double y2, double z2, Color fill, Color line) {
        box(x1, y1, z1, x2, y2, z2, fill, line, ShapeMode.Both);
    }

    public static void box(AABB box, Color fill, Color line) {
        if (box == null) return;
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, ShapeMode.Both);
    }

    public static void box(AABB box, Color fill, Color line, ShapeMode shapeMode) {
        if (box == null) return;
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, shapeMode, false);
    }

    public static void box(AABB box, Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion) {
        if (box == null) return;
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, shapeMode, ignoreOcclusion);
    }

    public static void box(AABB box, Color fill, Color line, ShapeMode shapeMode, boolean ignoreOcclusion, float lineWidth) {
        if (box == null) return;
        if (frameScope != null) {
            drawBoxRaw(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, shapeMode, ignoreOcclusion, lineWidth);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;
        try (var scope = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            drawBoxRaw(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, shapeMode, ignoreOcclusion, lineWidth);
        } catch (Exception ignored) {
        }
    }

    public static void line(double x1, double y1, double z1, double x2, double y2, double z2, Object color) {
        line(x1, y1, z1, x2, y2, z2, color, false);
    }

    public static void line(double x1, double y1, double z1, double x2, double y2, double z2, Object color, boolean ignoreOcclusion) {
        Color c = toAwt(color);
        if (c == null) return;

        if (frameScope != null) {
            var prop = Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), rgba(c), 1.5f);
            if (ignoreOcclusion) prop.setAlwaysOnTop();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;
        try (var scope = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            var prop = Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), rgba(c), 1.5f);
            if (ignoreOcclusion) prop.setAlwaysOnTop();
        } catch (Exception ignored) {
        }
    }
}