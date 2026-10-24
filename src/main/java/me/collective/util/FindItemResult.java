package me.collective.util;

import net.minecraft.world.InteractionHand;

public record FindItemResult(int slot, int count) {
    public boolean found() {
        return slot != -1;
    }

    public boolean isOffhand() {
        return slot == 40;
    }

    public boolean isHotbar() {
        return slot >= 0 && slot < 9;
    }

    public boolean isMain() {
        return slot >= 9 && slot < 36;
    }

    public InteractionHand getHand() {
        if (slot == 40) return InteractionHand.OFF_HAND;
        return InteractionHand.MAIN_HAND;
    }
}