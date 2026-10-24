package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.awt.Color;

/**
 * Отображает визуальный прогресс ломания блоков другими игроками и собой.
 */
public class BreakProgress extends Module {
    public enum ColorMode {
        GRADIENT, CUSTOM
    }

    public final BooleanSetting self = add(new BooleanSetting("Self", false));
    public final BooleanSetting shrink = add(new BooleanSetting("Shrink", true));
    public final EnumSetting<ColorMode> colorMode = add(new EnumSetting<>("ColorMode", ColorMode.GRADIENT));
    public final EnumSetting<ShapeMode> shapeMode = add(new EnumSetting<>("ShapeMode", ShapeMode.Both));
    public final ColorSetting customColor = add(new ColorSetting("CustomColor", new Color(255, 120, 0, 140)));

    public BreakProgress() {
        super("BreakProgress", "Отображает анимацию и процент прогресса ломания блоков вокруг.", Category.RENDER);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        var map = mc.level.destructionProgress();
        if (map == null || map.isEmpty()) return;

        for (java.util.SortedSet<net.minecraft.server.level.BlockDestructionProgress> set : map.values()) {
            if (set == null || set.isEmpty()) continue;
            net.minecraft.server.level.BlockDestructionProgress info = set.last();
            if (info == null) continue;
            if (info.getId() == mc.player.getId() && !self.getValue()) continue;

            int stage = info.getProgress();
            if (stage < 0 || stage > 9) continue;

            float progress = (stage + 1) / 10.0f;
            BlockPos pos = info.getPos();
            if (pos == null) continue;

            renderBreakBox(pos, progress);
        }
    }

    private void renderBreakBox(BlockPos pos, float progress) {
        AABB box;
        if (shrink.getValue()) {
            double inset = (1.0 - progress) * 0.5;
            box = new AABB(
                    pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                    pos.getX() + 1.0 - inset, pos.getY() + 1.0 - inset, pos.getZ() + 1.0 - inset
            );
        } else {
            box = new AABB(pos);
        }

        Color mainColor;
        if (colorMode.getValue() == ColorMode.GRADIENT) {
            // Зеленый (0%) -> Желтый (50%) -> Красный (100%)
            int red = (int) (Math.min(1.0f, progress * 2.0f) * 255);
            int green = (int) (Math.min(1.0f, (1.0f - progress) * 2.0f) * 255);
            mainColor = new Color(red, green, 30, 200);
        } else {
            mainColor = customColor.getValue();
        }

        Color fill = new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 70);
        Renderer3D.box(box, fill, mainColor, shapeMode.getValue(), false);
    }
}