package me.collective.module.highway;

import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import net.minecraft.core.BlockPos;

public class NegPosBorer extends BorerModule {
    public NegPosBorer() {
        super("NegPosBorer", "Копает диагональный хайвей в направлении -X +Z.", 1, 2, -2, 2);
    }

    @Override
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        BlockPos prevBlockPos = playerPos;
        playerPos = flooredPlayerPos();

        if (!playerPos.equals(prevBlockPos) || System.currentTimeMillis() - lastUpdateTime > 800) {
            switch (mode.getValue()) {
                case THIN -> {
                    do2x3(playerPos.offset(xOffset.getIntValue(), 0, zOffset.getIntValue()));
                    do2x3(playerPos.offset(xOffset.getIntValue() * -3, 0, zOffset.getIntValue() * -3));
                }
                case HIGHWAY -> {
                    doHighway4(playerPos.offset(xOffset.getIntValue(), 0, zOffset.getIntValue()));
                    doHighway4(playerPos.offset(xOffset.getIntValue() * -3, 0, zOffset.getIntValue() * -3));
                }
            }
            lastUpdateTime = System.currentTimeMillis();
        }
        packets = 0;
    }
}