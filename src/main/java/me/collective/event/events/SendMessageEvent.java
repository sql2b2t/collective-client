package me.collective.event.events;

import me.collective.event.CancellableEvent;

public class SendMessageEvent extends CancellableEvent {
    public String message;

    public SendMessageEvent(String message) {
        this.message = message;
    }
}