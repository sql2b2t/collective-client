package me.collective.event.events;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class Render3DEvent {
    public final MatrixStack matrices;
    public final Camera camera;
    public final float tickDelta;
    public final VertexConsumerProvider consumers;
    /** Все методы Renderer3D статические — разделяем один экземпляр вместо аллокации на каждый кадр. */
    public final me.collective.render.Renderer3D renderer = me.collective.render.Renderer3D.SHARED;

    public Render3DEvent(MatrixStack matrices, Camera camera, float tickDelta, VertexConsumerProvider consumers) {
        this.matrices = matrices;
        this.camera = camera;
        this.tickDelta = tickDelta;
        this.consumers = consumers;
    }

    public Render3DEvent(MatrixStack matrices, Camera camera, float tickDelta) {
        this(matrices, camera, tickDelta, null);
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public Camera getCamera() {
        return camera;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public VertexConsumerProvider getConsumers() {
        return consumers;
    }
}

