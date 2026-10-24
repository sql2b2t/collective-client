package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;

public class Sprint extends Module {
    public final BooleanSetting omni = add(new BooleanSetting("Omni", false));
    public final BooleanSetting grimSafe = add(new BooleanSetting("GrimSafe", true));

    public Sprint() {
        super("Sprint", "Автоматический спринт при любом движении игрока.", Category.MOVEMENT);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.isShiftKeyDown() || mc.player.getFoodData().getFoodLevel() <= 6) return;

        if (grimSafe.getValue()) {
            if (mc.player.input.hasForwardImpulse() && !mc.player.horizontalCollision) {
                mc.player.setSprinting(true);
            }
        } else if (omni.getValue()) {
            if (mc.player.input.hasForwardImpulse() || Math.abs(mc.player.input.getMoveVector().x) > 0) {
                mc.player.setSprinting(true);
            }
        } else {
            if (mc.player.input.hasForwardImpulse() && !mc.player.horizontalCollision) {
                mc.player.setSprinting(true);
            }
        }
    }
}