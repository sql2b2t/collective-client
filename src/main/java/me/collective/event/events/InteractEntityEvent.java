package me.collective.event.events;

import me.collective.event.CancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;

public class InteractEntityEvent extends CancellableEvent {
    public final Player player;
    public final Entity entity;
    public final InteractionHand hand;

    public InteractEntityEvent(Player player, Entity entity, InteractionHand hand) {
        this.player = player;
        this.entity = entity;
        this.hand = hand;
    }
}