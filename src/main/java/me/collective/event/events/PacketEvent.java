package me.collective.event.events;

import me.collective.event.CancellableEvent;
import net.minecraft.network.protocol.Packet;

public class PacketEvent {
    public static class Send extends CancellableEvent {
        public Packet<?> packet;

        public Send(Packet<?> packet) {
            this.packet = packet;
        }

        @SuppressWarnings("unchecked")
        public <T extends Packet<?>> T getPacket() {
            return (T) this.packet;
        }
    }

    public static class Receive extends CancellableEvent {
        public Packet<?> packet;

        public Receive(Packet<?> packet) {
            this.packet = packet;
        }

        @SuppressWarnings("unchecked")
        public <T extends Packet<?>> T getPacket() {
            return (T) this.packet;
        }
    }
}