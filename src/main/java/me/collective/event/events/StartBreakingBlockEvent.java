package me.collective.event.events;

import me.collective.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class StartBreakingBlockEvent extends CancellableEvent {
    public final BlockPos blockPos;
    public final Direction direction;

    public StartBreakingBlockEvent(BlockPos blockPos, Direction direction) {
        this.blockPos = blockPos;
        this.direction = direction;
    }
}