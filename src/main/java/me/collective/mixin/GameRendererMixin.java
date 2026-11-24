package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.gui.screens.ClientScreen;
import me.collective.client.render.GuiDrawUtils;
import me.collective.client.utils.math.Vec3Utils;
import me.collective.client.managers.ShaderRegistry;
import me.collective.client.render.RenderUtils;
import me.collective.client.events.HudRenderEvent;
import me.collective.client.render.GuiProjectionUtils;
import me.collective.client.utils.LockUtils;
import me.collective.client.modules.render.FreeLookModule;
import me.collective.client.modules.player.FreecamModule;
import me.collective.client.modules.client.HUDModule;
import me.collective.client.modules.player.NameProtectModule;
import me.collective.client.modules.render.NoBobModule;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.ShaderModule;
import me.collective.client.modules.render.ViewModelModule;
import me.collective.client.modules.render.ZoomModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   private static ZoomModule zoom;
   private static NoRenderModule noRenderModule;
   private static ShaderModule shader;
   private static FreecamModule freecam;
   private static FreeLookModule freelook;
   private static ViewModelModule viewmodel;
   private static NoBobModule noBob;
   private static HUDModule hud;
   private static ZoomModule zoom() {
      if (zoom == null) {
         zoom = ClientContext.getModule(ZoomModule.class);
      }
      return zoom;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static ShaderModule shader() {
      if (shader == null) {
         shader = ClientContext.getModule(ShaderModule.class);
      }
      return shader;
   }
   private static FreecamModule freecam() {
      if (freecam == null) {
         freecam = ClientContext.getModule(FreecamModule.class);
      }
      return freecam;
   }
   private static FreeLookModule freelook() {
      if (freelook == null) {
         freelook = ClientContext.getModule(FreeLookModule.class);
      }
      return freelook;
   }
   private static ViewModelModule viewmodel() {
      if (viewmodel == null) {
         viewmodel = ClientContext.getModule(ViewModelModule.class);
      }
      return viewmodel;
   }
   private static NoBobModule noBob() {
      if (noBob == null) {
         noBob = ClientContext.getModule(NoBobModule.class);
      }
      return noBob;
   }
   private static HUDModule hud() {
      if (hud == null) {
         hud = ClientContext.getModule(HUDModule.class);
      }
      return hud;
   }
   @Unique
   private GuiGraphics hudGraphics;
   @Final
   @Shadow
   private Minecraft minecraft;
   @Shadow
   @Final
   public GuiRenderer guiRenderer;
   @Shadow
   @Final
   public FogRenderer fogRenderer;
   @Unique
   private boolean collective$pickingFromFreecam = false;

   @Shadow
   public abstract void pick(float partialTick);

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
   private float invertViewBobSine(float originalSine) {
      return RenderUtils.isInverseViewBobbing() ? -originalSine : originalSine;
   }

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
   private float invertViewBobMagnitude(float originalMagnitude) {
      return RenderUtils.isInverseViewBobbing() ? -originalMagnitude : originalMagnitude;
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   public void initializeShaderRegistry(
      Minecraft client, ItemInHandRenderer handRenderer, RenderBuffers renderBuffers, BlockRenderDispatcher blockRenderer, CallbackInfo callback
   ) {
      ClientContext.shaderRegistry = new ShaderRegistry();
   }

   @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
   private void suppressHurtCamera(
      PoseStack poses, float partialTick, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled() && noRenderModule().hurtCamSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
   private void suppressTotemActivation(ItemStack stack, CallbackInfo callback) {
      if (stack.getItem() == Items.TOTEM_OF_UNDYING && noRenderModule().isToggled() && noRenderModule().totemOverlaySetting.getValue()) {
         callback.cancel();
      }
   }

   @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"), require = 0)
   private float suppressBlindnessInterpolation(
      float fraction, float start, float end, Operation original
   ) {
      return noRenderModule().isToggled() && noRenderModule().blindnessSetting.getValue() ? 0.0F : Mth.lerp(fraction, start, end);
   }

   @Inject(
      method = "renderLevel",
      at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=level")
   )
   private void captureWorldProjectionMatrix(
      DeltaTracker deltaTracker,
      CallbackInfo callback,
      @Local(ordinal = 0) Matrix4f projectionMatrix,
      @Local(ordinal = 1) Matrix4f unusedSecondMatrix,
      @Local(ordinal = 1) float unusedRenderFloat,
      @Local PoseStack poses
   ) {
      RenderUtils.WORLD_PROJECTION_MATRIX.set(projectionMatrix);
   }

   @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V"))
   private boolean shouldRenderHands(
      GameRenderer renderer, float partialTick, boolean ignoredHandFlag, Matrix4f projectionMatrix
   ) {
      return !shader().isToggled() || !shader().handsSetting.getValue();
   }

   @WrapWithCondition(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
   private boolean applyNoBobTranslation(
      PoseStack poseStack, float x, float y, float z
   ) {
      if (!noBob().isToggled()) {
         return true;
      }

      float multiplier = noBob().multiplierSetting.getValue();
      if (multiplier == 0.0F) {
         return true;
      }

      poseStack.translate(x * multiplier, y * multiplier, z * multiplier);
      return false;
   }

   @ModifyReturnValue(method = "getFov", at = @At(value = "RETURN", ordinal = 1))
   private float applyZoomAndViewModelFov(
      float originalFov, @Local(argsOnly = true) boolean useFovSetting
   ) {
      if (useFovSetting) {
         return originalFov;
      }

      float zoomScale = 1.0F - zoom().getZoomBlend();
      return viewmodel().isToggled() && viewmodel().viewModelFovSetting.getValue()
         ? (viewmodel().fovAmountSetting.getValue()).intValue() * zoomScale
         : originalFov * zoomScale;
   }

   @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
   private void prepareHandRendering(
      float partialTick, boolean ignoredHandFlag, Matrix4f projectionMatrix, CallbackInfo callback, @Share("backupMatrix") LocalRef backupMatrix
   ) {
      if (freecam().shouldHideHands()) {
         callback.cancel();
         return;
      }

      backupMatrix.set(new Matrix4f(projectionMatrix));
      RenderUtils.setRenderColor(
         1.0F,
         1.0F,
         1.0F,
         noRenderModule().getHandOpacity()
      );
      if (viewmodel().isToggled() && viewmodel().lighterSetting.getValue()) {
         float handYaw = -95.0F;
         float handPitch = 45.0F;
         Quaternionf handRotation = new Quaternionf().rotationYXZ((float)Math.toRadians(180.0F - handYaw), (float)Math.toRadians(-handPitch), 0.0F).conjugate();
         projectionMatrix.set(new Matrix4f().rotation(handRotation));
      }
   }

   @Inject(method = "renderItemInHand", at = @At("RETURN"))
   private void restoreHandRendering(
      float partialTick, boolean ignoredHandFlag, Matrix4f projectionMatrix, CallbackInfo callback, @Share("backupMatrix") LocalRef backupMatrix
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (viewmodel().isToggled() && viewmodel().lighterSetting.getValue()) {
         projectionMatrix.set((Matrix4fc)backupMatrix.get());
      }
   }

   @ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I"))
   private int applyViewModelHandLight(int originalLight) {
      return viewmodel().isToggled() && viewmodel().lighterSetting.getValue()
         ? 15728864
         : originalLight;
   }

   @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderScreenEffect(ZFLnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
   private boolean shouldRenderScreenEffects(
      ScreenEffectRenderer renderer, boolean ignoredEffectFlag, float partialTick, SubmitNodeCollector collector
   ) {
      return !ClientContext.shaderRegistry.renderingIntoTarget;
   }

   @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
   private void applyCameraPicking(float partialTick, CallbackInfo callback) {
      if (freecam().isToggled()
         && this.minecraft.getCameraEntity() != null
         && !this.collective$pickingFromFreecam) {
         callback.cancel();
         Entity cameraEntity = this.minecraft.getCameraEntity();
         double savedX = cameraEntity.getX();
         double savedY = cameraEntity.getY();
         double savedZ = cameraEntity.getZ();
         double savedPreviousX = cameraEntity.xo;
         double savedPreviousY = cameraEntity.yo;
         double savedPreviousZ = cameraEntity.zo;
         float savedYaw = cameraEntity.getYRot();
         float savedHeadYaw = cameraEntity.getYHeadRot();
         float savedPitch = cameraEntity.getXRot();
         float savedPreviousYaw = cameraEntity.yRotO;
         float savedPreviousPitch = cameraEntity.xRotO;
         cameraEntity.xo = freecam().previousCameraPosition.x;
         cameraEntity.yo = freecam().previousCameraPosition.y
            - cameraEntity.getEyeHeight(cameraEntity.getPose());
         cameraEntity.zo = freecam().previousCameraPosition.z;
         cameraEntity.setYRot(freecam().cameraYaw);
         cameraEntity.setYHeadRot(freecam().cameraYaw);
         cameraEntity.setXRot(freecam().cameraPitch);
         cameraEntity.yRotO = freecam().previousCameraYaw;
         cameraEntity.xRotO = freecam().previousCameraPitch;
         this.collective$pickingFromFreecam = true;
         try {
            LockUtils.runWithSharedLock(
               () -> {
                  Vec3Utils.setComponents(
                     cameraEntity.position(),
                     freecam().cameraPosition.x,
                     freecam().cameraPosition.y
                        - cameraEntity.getEyeHeight(cameraEntity.getPose()),
                     freecam().cameraPosition.z
                  );
                  this.pick(partialTick);
                  Vec3Utils.setComponents(
                     cameraEntity.position(), savedX, savedY, savedZ
                  );
               });
         } finally {
            this.collective$pickingFromFreecam = false;
            Vec3Utils.setComponents(cameraEntity.position(), savedX, savedY, savedZ);
            cameraEntity.xo = savedPreviousX;
            cameraEntity.yo = savedPreviousY;
            cameraEntity.zo = savedPreviousZ;
            cameraEntity.setYRot(savedYaw);
            cameraEntity.setYHeadRot(savedHeadYaw);
            cameraEntity.setXRot(savedPitch);
            cameraEntity.yRotO = savedPreviousYaw;
            cameraEntity.xRotO = savedPreviousPitch;
         }
      }

      if (freelook().isToggled() && !freecam().isToggled()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", shift = Shift.BEFORE)
   )
   private void beforeScreenRendering(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback, @Local GuiGraphics graphics
   ) {
      NameProtectModule.replacementBypassed = true;
      boolean manageProjection;
      if (this.minecraft.screen instanceof ClientScreen clientScreen) {
         if (clientScreen.hasMenuParent()) {
            clientScreen.getParentScreen()
               .renderWithTooltipAndSubtitles(
                  graphics,
                  -99,
                  -99,
                  RenderUtils.getPartialTick()
               );
         }
         manageProjection = false;
      } else if (this.minecraft.screen != null) {
         manageProjection = true;
      } else {
         return;
      }
      this.guiRenderer.render(this.fogRenderer.getBuffer(FogMode.NONE));
      this.guiRenderer.incrementFrameNumber();
      RenderUtils.clearDepth(this.minecraft.getMainRenderTarget());
      if (!manageProjection) {
         GuiProjectionUtils.beginGuiProjection();
      }
      this.renderClientHud(graphics, manageProjection);
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", shift = Shift.AFTER)
   )
   private void afterScreenRendering(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      NameProtectModule.replacementBypassed = false;
      if (this.minecraft.screen instanceof ClientScreen) {
         GuiProjectionUtils.restoreProjection();
      }
   }

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0)
   )
   private Overlay captureHudGraphics(
      Overlay overlay, @Local GuiGraphics graphics
   ) {
      this.hudGraphics = graphics;
      PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
      if (ClientAccess.collectiveMinecraft.level != null
         && !ClientAccess.collectiveMinecraft.options.hideGui
         && !ClientAccess.collectiveMinecraft.debugEntries.isOverlayVisible()) {
         GuiProjectionUtils.beginGuiProjection();
         HudRenderEvent.Before event = HudRenderEvent.Before.create(
            poses, graphics, RenderUtils.getPartialTick()
         );
         ClientAccess.eventBus.post(event);
         GuiDrawUtils.flush();
         GuiProjectionUtils.restoreProjection();
      }
      return overlay;
   }

   @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;incrementFrameNumber()V", shift = Shift.AFTER))
   private void renderHudWithoutScreen(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      if (this.minecraft.screen == null) {
         RenderUtils.clearDepth(this.minecraft.getMainRenderTarget());
         this.renderClientHud(
            this.hudGraphics, true
         );
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void updateFrameTimingAndChatHeight(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      hud().updateChatInputHeight(
         ClientAccess.collectiveMinecraft.screen instanceof ChatScreen
            ? 12.0F
            : 0.0F
      );
      ClientContext.frameTiming.recordFrame();
   }

   @Unique
   public void renderClientHud(GuiGraphics graphics, boolean manageProjection) {
      if (ClientAccess.collectiveMinecraft.level == null) {
         return;
      }
      boolean nameReplacementWasBypassed = NameProtectModule.replacementBypassed;
      if (nameReplacementWasBypassed) {
         NameProtectModule.replacementBypassed = false;
      }

      if (manageProjection) {
         GuiProjectionUtils.beginGuiProjection();
      }

      PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
      if (!ClientAccess.collectiveMinecraft.options.hideGui
         && !ClientAccess.collectiveMinecraft.debugEntries.isOverlayVisible()) {
         HudRenderEvent hudEvent = HudRenderEvent.create(
            poses, graphics, RenderUtils.getPartialTick()
         );
         ClientAccess.eventBus
            .post(hudEvent);
      }

      HudRenderEvent.Unconditional unconditionalEvent = HudRenderEvent.Unconditional.create(
         poses, graphics, RenderUtils.getPartialTick()
      );
      ClientAccess.eventBus
         .post(unconditionalEvent);
      GuiDrawUtils.flush();
      if (manageProjection) {
         GuiProjectionUtils.restoreProjection();
      }

      if (nameReplacementWasBypassed) {
         NameProtectModule.replacementBypassed = true;
      }
   }
}
