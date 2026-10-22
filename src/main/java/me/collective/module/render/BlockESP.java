package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.ColorUtil;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BlockListSetting;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/**
 * Порт BlockESP из Meteor Client.
 *
 * <p>Подсветка любых блоков через стены; список выбирается в GUI
 * (меню выбора блоков с поиском и чекбоксами).
 */
public class BlockESP extends Module {
    public final BlockListSetting blocks = add(new BlockListSetting.Builder()
        .name("Блоки")
        .defaultValue(List.of(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.CHEST,
            Blocks.ENDER_CHEST,
            Blocks.SPAWNER))
        .build());
    public final NumberSetting range = add(new NumberSetting("Радиус", 32.0, 8.0, 64.0, 1.0));
    public final ColorSetting color = add(new ColorSetting("Цвет", new Color(255, 80, 80, 120)));
    public final ModeSetting shape = add(new ModeSetting("Форма", "Both", "Both", "Sides", "Lines"));
    public final BooleanSetting tracers = add(new BooleanSetting("Линии", false));

    private final List<BlockPos> found = new ArrayList<>();
    private final BlockScan scan = new BlockScan();

    public BlockESP() {
        super("BlockESP", "Подсветка выбранных блоков сквозь стены.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        found.clear();
        scan.clear();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) {
            found.clear();
            scan.clear();
            return;
        }
        List<BlockPos> scanned = scan.tick(mc.world, mc.player.getBlockPos(), range.getIntValue(),
            new HashSet<>(blocks.getValue()), System.currentTimeMillis());
        found.clear();
        found.addAll(scanned);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || found.isEmpty() || mc.player == null) return;
        Color fill = color.getValue();
        Color line = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 255);
        ShapeMode mode = switch (shape.compute().trim()) {
            case "Sides" -> ShapeMode.Sides;
            case "Lines" -> ShapeMode.Lines;
            default -> ShapeMode.Both;
        };
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        double rangeBlocks = this.range.getValue();
        // found меняется только в тиковых обработчиках того же потока —
        // обход напрямую, без защитной копии на каждый кадр
        for (BlockPos pos : found) {
            Box box = new Box(pos);
            double dist = Math.sqrt(mc.player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            Color fillF = ColorUtil.fadeByDistance(dist, rangeBlocks, fill);
            Color lineF = ColorUtil.fadeLineByDistance(dist, rangeBlocks, fill, 255);
            Renderer3D.box(box, fillF, lineF, mode, true);
            if (tracers.getValue()) {
                Renderer3D.line(cam.x, cam.y, cam.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, lineF);
            }
        }
    }
}
