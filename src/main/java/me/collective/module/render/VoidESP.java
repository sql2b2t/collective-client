package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Подсвечивает дыры в бедроке, ведущие в бездну (для защиты на спавне 2b2t и в Незере).
 */
public class VoidESP extends Module {
    public final NumberSetting radius = add(new NumberSetting("Radius", 16.0, 4.0, 32.0, 1.0));
    public final BooleanSetting renderFloor = add(new BooleanSetting("Floor", true));
    public final BooleanSetting renderCeiling = add(new BooleanSetting("Ceiling", true));
    public final EnumSetting<ShapeMode> shapeMode = add(new EnumSetting<>("ShapeMode", ShapeMode.Both));

    public final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 30, 30, 80)));
    public final ColorSetting outline = add(new ColorSetting("Outline", new Color(255, 0, 0, 255)));

    private final List<BlockPos> voidHoles = new ArrayList<>();
    private int scanTimer = 0;

    public VoidESP() {
        super("VoidESP", "Подсвечивает дыры в бедроке, ведущие в бездну.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        voidHoles.clear();
        scanTimer = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (scanTimer-- <= 0) {
            scanTimer = 3;
            scanVoidHoles();
        }
    }

    private void scanVoidHoles() {
        voidHoles.clear();

        BlockPos playerPos = mc.player.getBlockPos();
        int r = radius.getValue().intValue();
        int bottomY = mc.world.getBottomY();
        boolean isNether = mc.world.getRegistryKey() == Level.NETHER;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int worldX = playerPos.getX() + x;
                int worldZ = playerPos.getZ() + z;

                if (renderFloor.getValue()) {
                    mutable.set(worldX, bottomY, worldZ);
                    if (isVoidColumn(mutable, bottomY, bottomY + 4)) {
                        voidHoles.add(new BlockPos(worldX, bottomY, worldZ));
                    }
                }

                if (isNether && renderCeiling.getValue()) {
                    mutable.set(worldX, 127, worldZ);
                    if (isCeilingVoidColumn(mutable)) {
                        voidHoles.add(new BlockPos(worldX, 127, worldZ));
                    }
                }
            }
        }
    }

    private boolean isVoidColumn(BlockPos.Mutable pos, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            pos.setY(y);
            if (mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCeilingVoidColumn(BlockPos.Mutable pos) {
        for (int y = 123; y <= 127; y++) {
            pos.setY(y);
            if (mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || voidHoles.isEmpty()) return;

        Color fillColor = fill.getValue();
        Color lineColor = outline.getValue();
        ShapeMode mode = shapeMode.getValue();

        for (BlockPos pos : voidHoles) {
            Renderer3D.box(new AABB(pos), fillColor, lineColor, mode, true);
        }
    }
}