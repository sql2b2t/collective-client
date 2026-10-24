package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.render.ColorUtil;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * StorageESP: подсветка контейнеров для розыска баз.
 *
 * <p>Контейнеры сканируются раз в секунду (перекладывание в кэш целиком, без
 * инкрементальных споров с итерацией по кадру) и подсвечиваются рамкой с
 * мягким затуханием на дальней кромке радиуса — общий язык визуала с
 * остальными ESP модулями.
 */
public class StorageESP extends Module {
    public static final Color CHEST_TINT = new Color(255, 190, 88);
    public static final Color SHULKER_TINT = new Color(178, 132, 255);
    public static final Color ENDER_TINT = new Color(88, 221, 178);
    public static final Color BARREL_TINT = new Color(226, 172, 104);
    public static final Color HOPPER_TINT = new Color(140, 140, 150);
    public static final Color DISPENSER_TINT = new Color(120, 120, 120);

    public final BooleanSetting chests = add(new BooleanSetting("Chests", true));
    public final BooleanSetting shulkers = add(new BooleanSetting("Shulkers", true));
    public final BooleanSetting enderChests = add(new BooleanSetting("EnderChests", true));
    public final BooleanSetting barrels = add(new BooleanSetting("Barrels", true));
    public final BooleanSetting hoppers = add(new BooleanSetting("Hoppers", true));
    public final BooleanSetting dispensers = add(new BooleanSetting("Dispensers", true));
    public final BooleanSetting chatAlert = add(new BooleanSetting("ChatAlert", true));
    public final BooleanSetting highlight = add(new BooleanSetting("Highlight", true));
    public final NumberSetting range = add(new NumberSetting("Радиус", 24.0, 8.0, 48.0, 1.0, (v) -> highlight.getValue()));

    /** Подсветки готовятся в тике; в кадре только рисуем без пересчёта. */
    private final List<PosBox> toRender = new ArrayList<>();
    private final Set<BlockPos> alertedPositions = new HashSet<>();
    private int tickTimer = 0;

    public StorageESP() {
        super("StorageESP", "Подсветка контейнеров и алерты: сундуки, шалкеры, эндер-сундуки и бочки для розыска баз.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        alertedPositions.clear();
        toRender.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) {
            if (!alertedPositions.isEmpty()) alertedPositions.clear();
            toRender.clear();
            return;
        }

        toRender.clear();

        if (highlight.getValue()) {
            // Кэш рамок: тип → цвет; итерируем block entities напрямую —
            // кандидатов немного, полный проход раз в тик безопасен.
            double rangeSq = this.range.getValue() * this.range.getValue();
            for (BlockEntity be : mc.world.getBlockEntities()) {
                BlockPos pos = be.getPos();
                if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;

                Color tint = null;
                if (chests.getValue() && be instanceof ChestBlockEntity) tint = CHEST_TINT;
                else if (shulkers.getValue() && be instanceof ShulkerBoxBlockEntity) tint = SHULKER_TINT;
                else if (enderChests.getValue() && be instanceof EnderChestBlockEntity) tint = ENDER_TINT;
                else if (barrels.getValue() && be instanceof BarrelBlockEntity) tint = BARREL_TINT;
                else if (hoppers.getValue() && be instanceof HopperBlockEntity) tint = HOPPER_TINT;
                else if (dispensers.getValue() && (be instanceof DispenserBlockEntity || be instanceof DropperBlockEntity)) tint = DISPENSER_TINT;

                if (tint != null) toRender.add(new PosBox(pos, tint));
            }
        }

        tickTimer++;
        if (tickTimer < 20) return; // состояния чата один раз в секунду
        tickTimer = 0;

        if (alertedPositions.size() > 1000) {
            alertedPositions.clear();
        }

        if (chatAlert.getValue()) {
            for (BlockEntity be : mc.world.getBlockEntities()) {
                BlockPos pos = be.getPos();
                if (alertedPositions.contains(pos)) continue;

                String type = null;
                if (chests.getValue() && be instanceof ChestBlockEntity) type = "Chest";
                else if (shulkers.getValue() && be instanceof ShulkerBoxBlockEntity) type = "Shulker AABB";
                else if (enderChests.getValue() && be instanceof EnderChestBlockEntity) type = "Ender Chest";
                else if (barrels.getValue() && be instanceof BarrelBlockEntity) type = "Barrel";
                else if (hoppers.getValue() && be instanceof HopperBlockEntity) type = "Hopper";
                else if (dispensers.getValue() && be instanceof DispenserBlockEntity) type = "Dispenser";
                else if (dispensers.getValue() && be instanceof DropperBlockEntity) type = "Dropper";

                if (type != null) {
                    alertedPositions.add(pos);
                    double dist = mc.player.getEyePos().distanceTo(pos.toCenterPos());
                    if (dist > 8.0) { // only alert if not right next to player
                        mc.player.sendMessage(Component.literal(String.format("§7[§6StorageESP§7] §e%s §7found at §aX: %d, Y: %d, Z: %d §7(§f%.0fm§7)",
                            type, pos.getX(), pos.getY(), pos.getZ(), dist)), false);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isToggled() || toRender.isEmpty() || mc.player == null) return;

        double rangeBlocks = this.range.getValue();
        for (PosBox pb : toRender) {
            BlockPos pos = pb.pos();
            double dist = Math.sqrt(mc.player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            Color tint = pb.color();
            Color fill = ColorUtil.fadeByDistance(dist, rangeBlocks,
                new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 70));
            Color line = ColorUtil.fadeLineByDistance(dist, rangeBlocks, tint, 220);
            Renderer3D.box(new AABB(pos), fill, line, ShapeMode.Both, true);
        }
    }

    private record PosBox(BlockPos pos, Color color) {}
}