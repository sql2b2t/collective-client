package me.collective.mixin;

import me.collective.module.render.Ambience;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Inject(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void collective$onApplyFog(ByteBuffer buffer, int offset, Vector4f color, float envStart, float envEnd, float rdStart, float rdEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
        Ambience ambience = Ambience.INSTANCE;
        if (ambience != null && ambience.isToggled()) {
            if (ambience.modifyFogBuffer(buffer, offset, color, envStart, envEnd, rdStart, rdEnd, skyEnd, cloudEnd)) {
                ci.cancel();
            }
        }
    }
}