package me.collective.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import me.collective.render.RenderFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Захватывает матрицы кадра в начале WorldRenderer.render.
 *
 * <p>{@code positionMatrix} — чистый поворот камеры без трансляции;
 * {@code basicProjectionMatrix} — фактическая проекция мира: FOV с учётом
 * спринта/зума плюс hurt-tilt, view-bobbing и эффект тошноты (именно она
 * ставится в RenderSystem для рендера уровня); {@code cullProjectionMatrix}
 * (третий Matrix4f) — облегчённый вариант для frustum-отсечения.
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void collective$captureFrameMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter,
                                                 boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix,
                                                 Matrix4f basicProjectionMatrix, Matrix4f cullProjectionMatrix,
                                                 GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                                                 CallbackInfo ci) {
        RenderFrame.capture(camera, positionMatrix, basicProjectionMatrix);
    }
}