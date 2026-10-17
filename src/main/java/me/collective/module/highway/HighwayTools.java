package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.combat.AutoLog;
import me.collective.module.movement.SafeWalk;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.item.Items;

import java.util.List;

/**
 * Порт HighwayTools: оркестратор — включает связку модулей под профиль
 * (стройка, копка по осям/диагоналям, кольцевые).
 * Без FreeLook и InstantRebreak — этих модулей в клиенте нет.
 */
public class HighwayTools extends Module {
    public enum Mode {
        HighwayBuilding,
        AxisDigging,
        NegNegDigging,
        NegPosDigging,
        PosNegDigging,
        PosPosDigging,
        RingRoadDigging
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Profile", Mode.HighwayBuilding));
    public final BooleanSetting axisToggle = add(new BooleanSetting("AxisToggle", true));
    public final NumberSetting radius = add(new NumberSetting("AxisRadius", 3, 0, 15, 1));
    public final BooleanSetting pickToggle = add(new BooleanSetting("PickToggle", true));
    public final BooleanSetting keepY = add(new BooleanSetting("KeepY", true));

    private final List<Class<? extends Module>> commonClasses = List.of(
        AutoLog.class,
        OffhandManager.class
    );

    private final List<Class<? extends Module>> diggingClasses = List.of(
        HotbarManager.class,
        LiquidFillerHIG.class,
        SafeWalk.class,
        ScaffoldHIG.class
    );

    private double originX;
    private double originY;
    private double originZ;
    private double originXZRatio;

    public HighwayTools() {
        super("HighwayTools", "Digs, builds and repairs highways automatically.", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        originX = Math.abs(mc.player.getX());
        originY = Math.abs(mc.player.getY());
        originZ = Math.abs(mc.player.getZ());
        originXZRatio = Math.abs(originX - originZ);

        switch (mode.getValue()) {
            case HighwayBuilding -> {
                enable(HighwayBuilderHIG.class);
                enableAll(commonClasses);
            }
            case AxisDigging, RingRoadDigging -> {
                enable(AxisBorer.class);
                enableAll(commonClasses);
                enableAll(diggingClasses);
            }
            case NegNegDigging -> {
                enable(NegNegBorer.class);
                enable(AutoCenter.class);
                enableAll(commonClasses);
                enableAll(diggingClasses);
            }
            case NegPosDigging -> {
                enable(NegPosBorer.class);
                enable(AutoCenter.class);
                enableAll(commonClasses);
                enableAll(diggingClasses);
            }
            case PosNegDigging -> {
                enable(PosNegBorer.class);
                enable(AutoCenter.class);
                enableAll(commonClasses);
                enableAll(diggingClasses);
            }
            case PosPosDigging -> {
                enable(PosPosBorer.class);
                enable(AutoCenter.class);
                enableAll(commonClasses);
                enableAll(diggingClasses);
            }
        }
    }

    @Override
    public void onDisable() {
        switch (mode.getValue()) {
            case HighwayBuilding -> {
                disableIfOn(HighwayBuilderHIG.class);
                disableAll(commonClasses);
            }
            case AxisDigging, RingRoadDigging -> {
                disableIfOn(AxisBorer.class);
                disableAll(commonClasses);
                disableAll(diggingClasses);
            }
            case NegNegDigging -> {
                disableIfOn(NegNegBorer.class);
                disableIfOn(AutoCenter.class);
                disableAll(commonClasses);
                disableAll(diggingClasses);
            }
            case NegPosDigging -> {
                disableIfOn(NegPosBorer.class);
                disableIfOn(AutoCenter.class);
                disableAll(commonClasses);
                disableAll(diggingClasses);
            }
            case PosNegDigging -> {
                disableIfOn(PosNegBorer.class);
                disableIfOn(AutoCenter.class);
                disableAll(commonClasses);
                disableAll(diggingClasses);
            }
            case PosPosDigging -> {
                disableIfOn(PosPosBorer.class);
                disableIfOn(AutoCenter.class);
                disableAll(commonClasses);
                disableAll(diggingClasses);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (axisToggle.getValue()) {
            if (originX >= radius.getValue() && originZ >= radius.getValue() && originXZRatio >= 4) {
                if (Math.abs(mc.player.getX()) <= radius.getValue() || Math.abs(mc.player.getZ()) <= radius.getValue()
                    || Math.abs(Math.abs(mc.player.getX()) - Math.abs(mc.player.getZ())) <= radius.getValue() + 4) {
                    info("Reached axis, disabling.");
                    setToggled(false);
                }
            }
        }

        if (pickToggle.getValue()) {
            FindItemResult pickaxe = InventoryUtil.find(stack ->
                stack.isOf(Items.DIAMOND_PICKAXE) || stack.isOf(Items.NETHERITE_PICKAXE));
            if (!pickaxe.found()) {
                error("No pickaxe found, disabling.");
                setToggled(false);
            }
        }

        if (keepY.getValue()) {
            if (mc.player.getY() < originY - 0.125) {
                info("Fell below original height, disabling.");
                setToggled(false);
            }
        }
    }

    private static void enable(Class<? extends Module> clazz) {
        Module m = ModuleManager.INSTANCE.getModule(clazz);
        if (m != null && !m.isToggled()) m.setToggled(true);
    }

    private static void enableAll(List<Class<? extends Module>> classes) {
        for (Class<? extends Module> clazz : classes) enable(clazz);
    }

    private static void disableIfOn(Class<? extends Module> clazz) {
        Module m = ModuleManager.INSTANCE.getModule(clazz);
        if (m != null && m.isToggled()) m.setToggled(false);
    }

    private static void disableAll(List<Class<? extends Module>> classes) {
        for (Class<? extends Module> clazz : classes) disableIfOn(clazz);
    }
}
