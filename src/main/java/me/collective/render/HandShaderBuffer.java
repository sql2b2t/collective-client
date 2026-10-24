package me.collective.render;

import me.collective.module.render.HandsShader;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Decorating {@link VertexConsumer} that overrides per-vertex colors with the
 * animated HandsShader palette while keeping the texture, UV, light and normals
 * from the original draw. All vertex writes inside the first-person hand pass
 * (model parts and items) converge onto the 11-argument vertex method, so this
 * single override recolors both bare hands and held items.
 */
public class HandShaderBuffer implements VertexConsumer {
    private final VertexConsumer delegate;

    public HandShaderBuffer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return delegate.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return delegate.color(r, g, b, a);
    }

    @Override
    public VertexConsumer color(int color) {
        // Color set explicitly through the shaded vertex path; pass through otherwise.
        return delegate.color(color);
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return delegate.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return delegate.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return delegate.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return delegate.normal(x, y, z);
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        return delegate.lineWidth(width);
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        delegate.vertex(x, y, z, HandsShader.shade(x, y, z, u, v, color, nx, ny, nz), u, v, overlay, light, nx, ny, nz);
    }
}