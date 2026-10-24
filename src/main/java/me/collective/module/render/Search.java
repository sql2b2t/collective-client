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
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Порт Search из Meteor Client.
 *
 * <p>Подсветка блоков сквозь стены: руды, хранилища, спавнеры.
 * Главный инструмент поиска чужих баз.
 */
public class Search extends Module {
    public final ModeSetting preset = add(new ModeSetting("Набор", "Хранилища", "Руды", "Хранилища", "Спавнеры", "Всё"));
    public final NumberSetting range = add(new NumberSetting("Радиус", 32.0, 8.0, 48.0, 1.0));
    public final ColorSetting color = add(new ColorSetting("Цвет", new Color(255, 170, 0, 120)));
    public final BooleanSetting tracers = add(new BooleanSetting("Линии", false));

    private final List<BlockPos> found = new ArrayList<>();
    private final BlockScan scan = new BlockScan();

    private static final Set<Block> ORES = Set.of(
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.ANCIENT_DEBRIS, Blocks.NETHER_QUARTZ_ORE);

    private static final Set<Block> STORAGE = buildStorageSet();

    private static Set<Block> buildStorageSet() {
        Set<Block> set = new HashSet<>(Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
            Blocks.ENDER_CHEST, Blocks.HOPPER, Blocks.FURNACE,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER,
            Blocks.SHULKER_BOX
        ));
        set.addAll(Blocks.DYED_SHULKER_BOX.asList());
        return Set.copyOf(set);
    }

    public Search() {
        super("Search", "Подсветка руд и хранилищ сквозь стены.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        found.clear();
        scan.clear();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) {
            found.clear();
            scan.clear();
            return;
        }

        Set<Block> targets = new HashSet<>();
        String p = preset.compute().trim();
        if (p.equals("Руды") || p.equals("Всё")) targets.addAll(ORES);
        if (p.equals("Хранилища") || p.equals("Всё")) targets.addAll(STORAGE);
        if (p.equals("Спавнеры") || p.equals("Всё")) {
            targets.add(Blocks.SPAWNER);
            targets.add(Blocks.TRIAL_SPAWNER);
        }

        List<BlockPos> scanned = scan.tick(mc.level, mc.player.blockPosition(), range.getIntValue(),
            targets, System.currentTimeMillis());
        found.clear();
        found.addAll(scanned);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || found.isEmpty() || mc.player == null) return;
        Color fill = color.getValue();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double rangeBlocks = this.range.getValue();
        // found меняется только в тиковых обработчиках того же потока —
        // обход напрямую, без защитной копии на каждый кадр
        for (BlockPos pos : found) {
            AABB box = new AABB(pos);
            double dist = Math.sqrt(mc.player.distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            Color fillF = ColorUtil.fadeByDistance(dist, rangeBlocks, fill);
            Color lineF = ColorUtil.fadeLineByDistance(dist, rangeBlocks, fill, 255);
            Renderer3D.box(box, fillF, lineF, ShapeMode.Both, true);
            if (tracers.getValue()) {
                Renderer3D.line(cam.x, cam.y, cam.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, lineF);
            }
        }
    }
}