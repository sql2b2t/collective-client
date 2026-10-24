package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.HighwayUtils;

/**
 * Автоматически центрирует игрока на оси или диагонали хайвея.
 * В режиме Input использует ванильный ввод (стрейфы), обходя проверку физики и движения GrimAC.
 */
public class AutoCenter extends Module {
    public enum CenterMode {
        Input,
        Velocity
    }

    public final EnumSetting<CenterMode> mode = add(new EnumSetting<>("Mode", CenterMode.Input));
    public final NumberSetting offset = add(new NumberSetting("Offset", 0.0, -2.5, 2.5, 0.1));
    public final NumberSetting maxSpeed = add(new NumberSetting("MaxSpeed", 0.1, 0.01, 0.5, 0.01));

    private int highway = -1;
    private boolean pressedLeft = false;
    private boolean pressedRight = false;

    public AutoCenter() {
        super("AutoCenter", "Автоматически центрирует игрока на блоке для ровного копания хайвеев.", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        highway = HighwayUtils.getHighway();
        pressedLeft = false;
        pressedRight = false;
    }

    @Override
    public void onDisable() {
        releaseKeys();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        check();
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (mode.getValue() == CenterMode.Velocity) {
            check();
        }
    }

    private void check() {
        if (mc.player == null || mc.level == null) return;

        highway = HighwayUtils.getHighway();
        if (highway == -1) {
            releaseKeys();
            setToggled(false);
            return;
        }

        double max = maxSpeed.getValue();
        double comp = offset.getValue();

        double diffX = 0;
        double diffZ = 0;

        if (highway == 1 || highway == 2) {
            // Главные хайвеи по оси X: центрируем Z
            double targetZ = (comp >= 0 ? 0.5 : -0.5) + comp;
            diffZ = targetZ - mc.player.getZ();
        } else if (highway == 3 || highway == 4) {
            // Главные хайвеи по оси Z: центрируем X
            double targetX = (comp >= 0 ? 0.5 : -0.5) + comp;
            diffX = targetX - mc.player.getX();
        } else if (highway == 5) {
            double addZ = mc.player.getZ() - mc.player.getX();
            diffZ = comp - addZ;
        } else if (highway == 6) {
            double addX = Math.abs(mc.player.getX()) - mc.player.getZ();
            diffX = addX - comp;
        } else if (highway == 7) {
            double addX = mc.player.getX() - Math.abs(mc.player.getZ());
            diffX = comp - addX;
        } else if (highway == 8) {
            double addZ = Math.abs(mc.player.getZ()) - Math.abs(mc.player.getX());
            diffZ = addZ - comp;
        }

        if (mode.getValue() == CenterMode.Input) {
            // Grim-Safe: проецируем вектор смещения на локальную ось стрейфа игрока (+right)
            float rad = (float) Math.toRadians(mc.player.getYRot());
            double strafeDot = -diffX * Math.cos(rad) - diffZ * Math.sin(rad);

            if (strafeDot > 0.04) {
                // Цель справа от игрока
                mc.options.keyRight.setDown(true);
                mc.options.keyLeft.setDown(false);
                pressedRight = true;
                pressedLeft = false;
            } else if (strafeDot < -0.04) {
                // Цель слева от игрока
                mc.options.keyLeft.setDown(true);
                mc.options.keyRight.setDown(false);
                pressedLeft = true;
                pressedRight = false;
            } else {
                releaseKeys();
            }
        } else {
            // Legacy режим прямого изменения скорости
            if (highway == 1 || highway == 2) {
                mc.player.push(0.0, 0.0, Math.max(-max, Math.min(max, diffZ)));
            } else if (highway == 3 || highway == 4) {
                mc.player.push(Math.max(-max, Math.min(max, diffX)), 0.0, 0.0);
            } else if (highway == 5) {
                mc.player.push(0.0, 0.0, Math.max(-max, Math.min(max, diffZ)));
            } else if (highway == 6) {
                mc.player.push(Math.max(-max, Math.min(max, diffX)), 0.0, 0.0);
            } else if (highway == 7) {
                mc.player.push(Math.max(-max, Math.min(max, diffX)), 0.0, 0.0);
            } else if (highway == 8) {
                mc.player.push(0.0, 0.0, Math.max(-max, Math.min(max, diffZ)));
            }
        }
    }

    private void releaseKeys() {
        if (pressedLeft) {
            mc.options.keyLeft.setDown(false);
            pressedLeft = false;
        }
        if (pressedRight) {
            mc.options.keyRight.setDown(false);
            pressedRight = false;
        }
    }
}