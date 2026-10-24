package me.collective.event.events;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public class RenderHeldItemEvent {
    private final AbstractClientPlayerEntity player;
    private final InteractionHand hand;
    private final ItemStack item;
    private final PoseStack matrices;
    private final OrderedRenderCommandQueue renderCommands;
    private final int light;

    public RenderHeldItemEvent(AbstractClientPlayerEntity player, InteractionHand hand, ItemStack item, PoseStack matrices, OrderedRenderCommandQueue renderCommands, int light) {
        this.player = player;
        this.hand = hand;
        this.item = item;
        this.matrices = matrices;
        this.renderCommands = renderCommands;
        this.light = light;
    }

    public AbstractClientPlayerEntity getPlayer() {
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

    public OrderedRenderCommandQueue getRenderCommands() {
        return renderCommands;
    }

    public int getLight() {
        return light;
    }
}