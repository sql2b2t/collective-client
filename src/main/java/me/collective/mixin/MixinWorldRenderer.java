package me.collective.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import me.collective.render.RenderFrame;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void collective$captureFrameMatrices(
            GraphicsResourceAllocator allocator,
            boolean bl,
            CameraRenderState cameraRenderState,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            boolean bl2,
            CallbackInfo ci) {
        RenderFrame.capture(cameraRenderState);
    }
}