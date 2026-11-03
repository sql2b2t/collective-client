package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;

import java.awt.Color;

/**
 * Порт AxisViewer: линии осей мира (кардиналы и диагонали) на заданной высоте.
 */
public class AxisViewer extends Module {
    public enum AxisType {
        Both,
        Cardinals,
        Diagonals,
        None;

        boolean cardinals() {
            return this == Both || this == Cardinals;
        }

        boolean diagonals() {
            return this == Both || this == Diagonals;
        }
    }

    public final EnumSetting<AxisType> overworldAxes = add(new EnumSetting<>("Overworld", AxisType.Both));
    public final NumberSetting overworldY = add(new NumberSetting("OverworldY", 63, -64, 319, 1));
    public final ColorSetting overworldColor = add(new ColorSetting("OverworldColor", new Color(25, 25, 225, 255)));
    public final EnumSetting<AxisType> netherAxes = add(new EnumSetting<>("Nether", AxisType.Both));
    public final NumberSetting netherY = add(new NumberSetting("NetherY", 120, 0, 255, 1));
    public final ColorSetting netherColor = add(new ColorSetting("NetherColor", new Color(225, 25, 25, 255)));
    public final EnumSetting<AxisType> endAxes = add(new EnumSetting<>("End", AxisType.Both));
    public final NumberSetting endY = add(new NumberSetting("EndY", 64, 0, 255, 1));
    public final ColorSetting endColor = add(new ColorSetting("EndColor", new Color(225, 25, 25, 255)));

    public AxisViewer() {
        super("AxisViewer", "Отображает мировые оси координат X, Y и Z.", Category.HIGHWAY);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isToggled() || mc.world == null || mc.options.hudHidden) return;

        String dim = mc.world.getRegistryKey().getValue().toString();
        AxisType axisType;
        int y;
        Color lineColor;
        if (dim.endsWith("the_nether")) {
            axisType = netherAxes.getValue();
            y = netherY.getIntValue();
            lineColor = netherColor.getValue();
        } else if (dim.endsWith("the_end")) {
            axisType = endAxes.getValue();
            y = endY.getIntValue();
            lineColor = endColor.getValue();
        } else {
            axisType = overworldAxes.getValue();
            y = overworldY.getIntValue();
            lineColor = overworldColor.getValue();
        }

        if (axisType == AxisType.None) return;
        double renderY = y;

        if (axisType.cardinals()) {
            drawSegmentedLine(0, renderY, 0, 30_000_000, renderY, 0, lineColor);
            drawSegmentedLine(0, renderY, 0, -30_000_000, renderY, 0, lineColor);
            drawSegmentedLine(0, renderY, 0, 0, renderY, 30_000_000, lineColor);
            drawSegmentedLine(0, renderY, 0, 0, renderY, -30_000_000, lineColor);
        }
        if (axisType.diagonals()) {
            drawSegmentedLine(0, renderY, 0, 30_000_000, renderY, 30_000_000, lineColor);
            drawSegmentedLine(0, renderY, 0, 30_000_000, renderY, -30_000_000, lineColor);
            drawSegmentedLine(0, renderY, 0, -30_000_000, renderY, 30_000_000, lineColor);
            drawSegmentedLine(0, renderY, 0, -30_000_000, renderY, -30_000_000, lineColor);
        }
    }

    private void drawSegmentedLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        double segmentLength = 100_000;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double totalLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int segments = (int) (totalLength / segmentLength);
        double nx = dx / totalLength;
        double ny = dy / totalLength;
        double nz = dz / totalLength;

        double cx = x1;
        double cy = y1;
        double cz = z1;
        for (int i = 0; i < segments; i++) {
            double ex = cx + nx * segmentLength;
            double ey = cy + ny * segmentLength;
            double ez = cz + nz * segmentLength;
            Renderer3D.line(cx, cy, cz, ex, ey, ez, color);
            cx = ex;
            cy = ey;
            cz = ez;
        }
        Renderer3D.line(cx, cy, cz, x2, y2, z2, color);
    }
}
