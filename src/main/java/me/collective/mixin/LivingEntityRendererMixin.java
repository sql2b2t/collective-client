package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.modules.render.AnimationsModule;
import me.collective.client.modules.render.ChamsModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.managers.RotationManager;
import me.collective.client.render.ColorUtils;
import me.collective.client.render.EntityRenderStateAccess;
import me.collective.client.events.LivingEntityModelSubmitEvent;
import me.collective.client.modules.render.NoRenderModule;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, priority = 9999)
public abstract class LivingEntityRendererMixin implements ClientAccess {
   @Shadow
   protected EntityModel model;
   private static AnimationsModule animationsModule;
   private static NoRenderModule noRenderModule;
   private static AnimationsModule animationsModule() {
      if (animationsModule == null) {
         animationsModule = ClientContext.getModule(AnimationsModule.class);
      }
      return animationsModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   @WrapWithCondition(
      method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
      ),
      require = 0
   )
   private boolean handleLivingModelSubmission(
      SubmitNodeCollector collector,
      Model model,
      Object originalModelState,
      PoseStack poses,
      RenderType renderType,
      int packedLight,
      int packedOverlay,
      int color,
      TextureAtlasSprite sprite,
      int originalOutlineColor,
      CrumblingOverlay crumblingOverlay,
      @Local(argsOnly = true) LivingEntityRenderState renderState
   ) {
      if (!ClientContext.shaderRegistry.renderingIntoTarget
         && EntityRenderStateAccess.getEntity(renderState) != null) {
         float redComponent = ARGB.red(color) / 255.0F;
         float greenComponent = ARGB.green(color) / 255.0F;
         float blueComponent = ARGB.blue(color) / 255.0F;
         float alphaComponent = ARGB.alpha(color) / 255.0F;
         LivingEntityModelSubmitEvent event = LivingEntityModelSubmitEvent.create(
            EntityRenderStateAccess.getEntity(renderState),
            packedLight,
            redComponent,
            greenComponent,
            blueComponent,
            alphaComponent
         );
         ClientAccess.eventBus.post(
            event
         );
         color = ColorUtils.packArgb(
            event.getRed(),
            event.getGreen(),
            event.getBlue(),
            event.getAlpha()
         );
         if (!event.isCancelled()) {
            collector.submitModel(
               model,
               renderState,
               poses,
               renderType,
               event.getLightCoords(),
               packedOverlay,
               color,
               (TextureAtlasSprite)null,
               renderState.outlineColor,
               (CrumblingOverlay)null
            );
         }

         return false;
      } else {
         return true;
      }
   }

   @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;layers:Ljava/util/List;"))
   private List filterShineRenderLayers(List originalLayers) {
      return ChamsModule.renderingShineModels ? Collections.emptyList() : originalLayers;
   }

   @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"))
   public boolean isPassengerForPlayerAnimation(
      boolean originalPassenger, @Local(argsOnly = true) LivingEntity entity
   ) {
      return animationsModule().isStaticPlayerAnimationEnabled()
            && entity instanceof Player
         ? true
         : entity.isPassenger();
   }

   @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
   private void applyLocalPlayerRenderRotations(
      LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo callback
   ) {
      if (!ClientAccess.isLocalPlayer(entity) || RotationManager.renderingInventory) {
         return;
      }

      RotationManager rotations = ClientContext.rotationManager;
      float headYaw = Mth.rotLerp(partialTick, rotations.getPreviousHeadYaw(), rotations.getHeadYaw());
      renderState.xRot = Mth.lerp(partialTick, rotations.getPreviousPitch(), rotations.getPitch());
      renderState.bodyRot = Mth.rotLerp(partialTick, rotations.getPreviousBodyYaw(), rotations.getBodyYaw());
      renderState.yRot = Mth.wrapDegrees(headYaw - renderState.bodyRot);
   }

   @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
   private void limitWardenRendering(
      LivingEntityRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      int maximumDistance = noRenderModule().wardenDistanceSetting.getValue();
      Minecraft client = Minecraft.getInstance();
      if (renderState instanceof WardenRenderState
         && noRenderModule().isToggled()
         && noRenderModule().wardensSetting.getValue()
         && new Vec3(renderState.x, renderState.y, renderState.z).distanceToSqr(client.gameRenderer.getMainCamera().position()) > maximumDistance * maximumDistance) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(method = "getOverlayCoords", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;v(Z)I"))
   private static int suppressHurtOverlay(int originalOverlay) {
      return noRenderModule().isToggled()
            && noRenderModule().hurtSetting.getValue()
         ? 10
         : originalOverlay;
   }
}
