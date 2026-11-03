package me.collective.event.events;

public class MouseScrollEvent {
    private final double amount;
    private boolean cancelled;

    public MouseScrollEvent(double amount) {
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
