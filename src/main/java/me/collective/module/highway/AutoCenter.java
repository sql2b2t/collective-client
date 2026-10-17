package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import me.collective.util.HighwayUtils;

/**
 * Порт AutoCenter: подтягивает игрока к оси, чтобы борeры копали ровно.
 */
public class AutoCenter extends Module {
    public final NumberSetting offset = add(new NumberSetting("Offset", 1.0, -2.5, 2.5, 0.1));
    public final NumberSetting maxSpeed = add(new NumberSetting("MaxSpeed", 0.1, 0.01, 0.5, 0.01));

    // x+ x- z+ z- x+z+ x-z+ x+z- x-z-
    private int highway = -1;

    public AutoCenter() {
        super("AutoCenter", "Automatically centers the player so that HighwayBorers mine on axis.", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        highway = HighwayUtils.getHighway();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        check();
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        check();
    }

    private void check() {
        if (mc.player == null || mc.world == null) return;

        double max = maxSpeed.getValue();
        double comp = offset.getValue();
        if (highway == -1) setToggled(false);
        if (highway == 5) {
            double addZ = mc.player.getZ() - mc.player.getX();
            mc.player.addVelocity(0.0, 0.0, Math.max(-max, Math.min(max, comp - addZ)));
        } else if (highway == 6) {
            double addX = Math.abs(mc.player.getX()) - mc.player.getZ();
            mc.player.addVelocity(Math.max(-max, Math.min(max, addX - comp)), 0.0, 0.0);
        } else if (highway == 7) {
            double addX = mc.player.getX() - Math.abs(mc.player.getZ());
            mc.player.addVelocity(Math.max(-max, Math.min(max, comp - addX)), 0.0, 0.0);
        } else if (highway == 8) {
            double addZ = Math.abs(mc.player.getZ()) - Math.abs(mc.player.getX());
            mc.player.addVelocity(0.0, 0.0, Math.max(-max, Math.min(max, addZ - comp)));
        }
    }
}
