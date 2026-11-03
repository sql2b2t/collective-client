package me.collective.module.highway;

import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import net.minecraft.util.math.BlockPos;

public class PosNegBorer extends BorerModule {
    public PosNegBorer() {
        super("PosNegBorer", "Копает диагональный хайвей в направлении +X -Z.", 1, 2, 2, -2);
    }

    @Override
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        BlockPos prevBlockPos = playerPos;
        playerPos = flooredPlayerPos();

        if (!playerPos.equals(prevBlockPos) || System.currentTimeMillis() - lastUpdateTime > 800) {
            switch (mode.getValue()) {
                case THIN -> {
                    do2x3(playerPos.add(xOffset.getIntValue(), 0, zOffset.getIntValue()));
                    do2x3(playerPos.add(xOffset.getIntValue() * -3, 0, zOffset.getIntValue() * -3));
                }
                case HIGHWAY -> {
                    doHighway4(playerPos.add(xOffset.getIntValue(), 0, zOffset.getIntValue()));
                    doHighway4(playerPos.add(xOffset.getIntValue() * -3, 0, zOffset.getIntValue() * -3));
                }
            }
            lastUpdateTime = System.currentTimeMillis();
        }
        packets = 0;
    }
}
