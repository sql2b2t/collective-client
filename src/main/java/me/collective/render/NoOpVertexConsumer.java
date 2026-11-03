package me.collective.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;

public final class NoOpVertexConsumer implements VertexConsumer {
    public static final NoOpVertexConsumer INSTANCE = new NoOpVertexConsumer();

    private NoOpVertexConsumer() {}

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer color(int color) {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue, float alpha, int[] lights, int overlay) {
    }
}
