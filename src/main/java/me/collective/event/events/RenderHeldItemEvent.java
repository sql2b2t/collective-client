package me.collective.event.events;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public class RenderHeldItemEvent {
    private final AbstractClientPlayer player;
    private final InteractionHand hand;
    private final ItemStack item;
    private final PoseStack matrices;
    private final SubmitNodeCollector renderCommands;
    private final int light;

    public RenderHeldItemEvent(AbstractClientPlayer player, InteractionHand hand, ItemStack item, PoseStack matrices, SubmitNodeCollector renderCommands, int light) {
        this.player = player;
        this.hand = hand;
        this.item = item;
        this.matrices = matrices;
        this.renderCommands = renderCommands;
        this.light = light;
    }

    public AbstractClientPlayer getPlayer() {
        return player;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getItem() {
        return item;
    }

    public PoseStack getMatrices() {
        return matrices;
    }

    public SubmitNodeCollector getRenderCommands() {
        return renderCommands;
    }

    public int getLight() {
        return light;
    }
}