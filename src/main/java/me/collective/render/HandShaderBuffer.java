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
    public VertexConsumer addVertex(float x, float y, float z) {
        return delegate.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return delegate.setColor(r, g, b, a);
    }

    @Override
    public VertexConsumer setColor(int color) {
        return delegate.setColor(color);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return delegate.setUv3(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return delegate.setNormal(x, y, z);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return delegate.setLineWidth(width);
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        delegate.addVertex(x, y, z, HandsShader.shade(x, y, z, u, v, color, nx, ny, nz), u, v, overlay, light, nx, ny, nz);
    }
}