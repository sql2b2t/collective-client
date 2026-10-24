package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Отрисовывает цветной шлейф пройденного пути за игроком.
 */
public class Breadcrumbs extends Module {
    public final NumberSetting timeout = add(new NumberSetting("Timeout", 15.0, 2.0, 60.0, 1.0));
    public final NumberSetting maxPoints = add(new NumberSetting("MaxPoints", 300.0, 50.0, 1000.0, 10.0));
    public final BooleanSetting fade = add(new BooleanSetting("Fade", true));
    public final ColorSetting color = add(new ColorSetting("Color", new Color(0, 220, 255, 220)));

    private record TrailPoint(Vec3 pos, long time) {}
    private final List<TrailPoint> points = new ArrayList<>();

    public Breadcrumbs() {
        super("Breadcrumbs", "Отображает шлейф пройденного пути за игроком.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        points.clear();
    }

    @Override
    public void onDisable() {
        points.clear();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Vec3 current = new Vec3(mc.player.getX(), mc.player.getY() + 0.05, mc.player.getZ());
        long now = System.currentTimeMillis();

        if (points.isEmpty() || points.get(points.size() - 1).pos.squaredDistanceTo(current) >= 0.04) {
            points.add(new TrailPoint(current, now));
        }

        long maxAgeMs = (long) (timeout.getValue() * 1000L);
        int max = maxPoints.getValue().intValue();

        for (Iterator<TrailPoint> it = points.iterator(); it.hasNext(); ) {
            TrailPoint p = it.next();
            if (now - p.time > maxAgeMs) {
                it.remove();
            }
        }

        while (points.size() > max) {
            points.remove(0);
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || points.size() < 2) return;

        long now = System.currentTimeMillis();
        long maxAgeMs = (long) (timeout.getValue() * 1000L);
        Color base = color.getValue();

        for (int i = 0; i < points.size() - 1; i++) {
            TrailPoint p1 = points.get(i);
            TrailPoint p2 = points.get(i + 1);

            Color c = base;
            if (fade.getValue()) {
                long age = now - p1.time;
                float progress = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) age / maxAgeMs));
                int alpha = (int) (base.getAlpha() * progress);
                if (alpha < 5) continue;
                c = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
            }

            Renderer3D.line(p1.pos.x, p1.pos.y, p1.pos.z, p2.pos.x, p2.pos.y, p2.pos.z, c);
        }
    }
}