package me.collective.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Accessor("resourcePool")
   CrossFrameResourcePool getPool();

   @Accessor("globalSettingsUniform")
   GlobalSettingsUniform getGlobalSettings();

   @Invoker("bobView")
   void collective$bobView(CameraRenderState cameraState, PoseStack poses);

   @Invoker("bobHurt")
   void collective$bobViewHurt(CameraRenderState cameraState, PoseStack poses);
}
