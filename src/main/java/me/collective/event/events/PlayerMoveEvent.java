package me.collective.event.events;

import me.collective.event.CancellableEvent;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public class PlayerMoveEvent extends CancellableEvent {
    public final MovementType type;
    public Vec3 movement;

    public PlayerMoveEvent(MovementType type, Vec3 movement) {
        this.type = type;
        this.movement = movement;
    }
}