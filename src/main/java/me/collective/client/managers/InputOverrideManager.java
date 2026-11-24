package me.collective.client.managers;

import me.collective.client.ClientAccess;
import me.collective.client.events.EventHandler;
import me.collective.client.events.KeyboardInputTickEvent;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.utils.InputUtils;
import java.util.function.Consumer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

public final class InputOverrideManager {
    private final PendingValue<Boolean> pendingSneak = new PendingValue<>();
    private final PendingValue<Boolean> pendingJump = new PendingValue<>();
    private final PendingValue<Boolean> pendingForward = new PendingValue<>();
    private final PendingValue<Boolean> pendingBack = new PendingValue<>();
    private final PendingValue<Boolean> pendingLeft = new PendingValue<>();
    private final PendingValue<Boolean> pendingRight = new PendingValue<>();
    private final PendingValue<Boolean> pendingSprint = new PendingValue<>();
    private final PendingValue<Float> pendingForwardMovement = new PendingValue<>();
    private final PendingValue<Float> pendingSidewaysMovement = new PendingValue<>();
    private final PendingFlag sprintBlock = new PendingFlag();

    public InputOverrideManager() {
        ClientAccess.eventBus.subscribe(this);
    }

    public void requestSneak(boolean value) {
        pendingSneak.set(value);
    }

    public void requestJump(boolean value) {
        pendingJump.set(value);
    }

    public void requestForward(boolean value) {
        pendingForward.set(value);
    }

    public void requestBack(boolean value) {
        pendingBack.set(value);
    }

    public void requestLeft(boolean value) {
        pendingLeft.set(value);
    }

    public void requestRight(boolean value) {
        pendingRight.set(value);
    }

    public void requestSprint(boolean value) {
        pendingSprint.set(value);
    }

    public void requestForwardMovement(float value) {
        pendingForwardMovement.set(value);
    }

    public void requestSidewaysMovement(float value) {
        pendingSidewaysMovement.set(value);
    }

    public boolean isSprintBlocked() {
        return sprintBlock.isPending();
    }

    public void blockSprint() {
        sprintBlock.markPending();
    }

    public Input getInput() {
        if (ClientAccess.collectiveMinecraft.player == null) {
            throw new IllegalStateException("Player is null");
        }
        return ClientAccess.collectiveMinecraft.player.input.keyPresses;
    }

    public void requestMovement(float forward, float sideways) {
        requestForwardMovement(forward);
        requestSidewaysMovement(sideways);
    }

    public void requestReset() {
        requestSneak(false);
        requestSprint(false);
        requestJump(false);
        requestForward(false);
        requestBack(false);
        requestLeft(false);
        requestRight(false);
        requestMovement(0.0F, 0.0F);
    }

    @EventHandler(priority = -100)
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        sprintBlock.reset();
    }

    @EventHandler(priority = -100)
    private void onKeyboardInputTickEvent(KeyboardInputTickEvent event) {
        ClientInput input = event.getInput();
        pendingSneak.consume(value -> InputUtils.setSneak(input, value));
        pendingJump.consume(value -> InputUtils.setJump(input, value));
        pendingForward.consume(value -> InputUtils.setForward(input, value));
        pendingBack.consume(value -> InputUtils.setBack(input, value));
        pendingLeft.consume(value -> InputUtils.setLeft(input, value));
        pendingRight.consume(value -> InputUtils.setRight(input, value));
        pendingSprint.consume(value -> InputUtils.setSprint(input, value));
        pendingForwardMovement.consume(value -> InputUtils.setForwardMovement(input, value));
        pendingSidewaysMovement.consume(value -> InputUtils.setSidewaysMovement(input, value));
    }

    private static final class PendingValue<T> extends PendingFlag {
        private T value;

        public void set(T value) {
            this.value = value;
            markPending();
        }

        void consume(Consumer<T> consumer) {
            if (isPending()) {
                consumer.accept(value);
                reset();
            }
        }
    }

    private static class PendingFlag {
        private boolean pending;

        boolean isPending() {
            return pending;
        }

        void markPending() {
            pending = true;
        }

        void reset() {
            pending = false;
        }
    }

}
