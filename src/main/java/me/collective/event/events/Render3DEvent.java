package me.collective.event.events;

import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class Render3DEvent {
    public interface BufferSource {
        VertexConsumer getBuffer(Object layer);
    }

    public final PoseStack matrices;
    public final Camera camera;
    public final float tickDelta;
    public final BufferSource consumers;
    /** Все методы Renderer3D статические — разделяем один экземпляр вместо аллокации на каждый кадр. */
    public final me.collective.render.Renderer3D renderer = me.collective.render.Renderer3D.SHARED;

    public Render3DEvent(PoseStack matrices, Camera camera, float tickDelta) {
        this(matrices, camera, tickDelta, null);
    }

    public Render3DEvent(PoseStack matrices, Camera camera, float tickDelta, BufferSource consumers) {
        this.matrices = matrices;
        this.camera = camera;
        this.tickDelta = tickDelta;
        this.consumers = consumers;
    }

    public PoseStack getMatrices() {
        return matrices;
    }

    public Camera getCamera() {
        return camera;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public BufferSource getConsumers() {
        return consumers;
    }
}
