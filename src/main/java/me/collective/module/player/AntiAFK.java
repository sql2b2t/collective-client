package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;

public class AntiAFK extends Module {
    public final BooleanSetting spin = add(new BooleanSetting("Spin", true));
    public final BooleanSetting jump = add(new BooleanSetting("Jump", false));

    private int jumpTimer = 0;

    public AntiAFK() {
        super("AntiAFK", "Предотвращает кик с сервера за бездействие и AFK-таймер.", Category.PLAYER);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (spin.getValue()) {
            mc.player.setYaw(mc.player.getYaw() + 1.0f);
        }

        if (jump.getValue()) {
            jumpTimer++;
            if (jumpTimer >= 200) { // jump every 10 seconds
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                }
                jumpTimer = 0;
            }
        }
    }
}
