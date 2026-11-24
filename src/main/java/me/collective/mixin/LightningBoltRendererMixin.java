package me.collective.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientContext;
import me.collective.client.render.ClientLightningState;
import me.collective.client.render.EntityRenderStateAccess;
import me.collective.client.modules.misc.KillEffectsModule;
import java.awt.Color;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LightningBoltRenderer.class)
public final class LightningBoltRendererMixin {
   private static KillEffectsModule killEffectsModule;
   private static KillEffectsModule killEffectsModule() {
      if (killEffectsModule == null) {
         killEffectsModule = ClientContext.getModule(KillEffectsModule.class);
      }
      return killEffectsModule;
   }
   @Unique
   private static boolean renderedClientLightning;

   @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"))
   private void captureSubmittingLightningBolt(
      LightningBoltRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      renderedClientLightning = entity instanceof ClientLightningState state
         && state.collective$isClient();
   }

   @ModifyArgs(method = "quad", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
   private static void overrideClientLightningTint(Args arguments) {
      if (renderedClientLightning
         && killEffectsModule().isToggled()
         && killEffectsModule().thunderSetting.getValue()) {
         Color lightningColor = killEffectsModule().colorSetting.getValue();
         arguments.set(0, lightningColor.getRed() / 255.0F);
         arguments.set(1, lightningColor.getGreen() / 255.0F);
         arguments.set(
            2,
            lightningColor.getBlue() / 255.0F
         );
      }
   }
}
