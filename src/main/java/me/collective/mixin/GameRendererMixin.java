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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.ModelManager;
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
   private GuiGraphicsExtractor hudGraphics;
   @Final
   @Shadow
   private Minecraft minecraft;
   @Shadow
   @Final
   public GuiRenderer guiRenderer;
   @Shadow
   @Final
   public FogRenderer fogRenderer;

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
   private float invertViewBobSine(float originalSine) {
      return RenderUtils.isInverseViewBobbing() ? -originalSine : originalSine;
   }

   @ModifyExpressionValue(method = "bobView", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
   private float invertViewBobMagnitude(float originalMagnitude) {
      return RenderUtils.isInverseViewBobbing() ? -originalMagnitude : originalMagnitude;
   }

   @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
   private void suppressHurtCamera(
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState, PoseStack poses, CallbackInfo callback
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
      at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=level"),
      require = 0
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

   @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"), require = 0)
   private boolean shouldRenderHands(
      GameRenderer renderer, net.minecraft.client.renderer.state.level.CameraRenderState cameraState, float partialTick, Matrix4fc projectionMatrix
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

   @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
   private void prepareHandRendering(
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState, float partialTick, Matrix4fc projectionMatrix, CallbackInfo callback
   ) {
      if (freecam().shouldHideHands()) {
         callback.cancel();
         return;
      }

      RenderUtils.setRenderColor(
         1.0F,
         1.0F,
         1.0F,
         noRenderModule().getHandOpacity()
      );
   }

   @Inject(method = "renderItemInHand", at = @At("RETURN"))
   private void restoreHandRendering(
      net.minecraft.client.renderer.state.level.CameraRenderState cameraState, float partialTick, Matrix4fc projectionMatrix, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @ModifyExpressionValue(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I"), require = 0)
   private int applyViewModelHandLight(int originalLight) {
      return viewmodel().isToggled() && viewmodel().lighterSetting.getValue()
         ? 15728864
         : originalLight;
   }

   @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderScreenEffect(ZFLnet/minecraft/client/renderer/SubmitNodeCollector;)V"), require = 0)
   private boolean shouldRenderScreenEffects(
      ScreenEffectRenderer renderer, boolean ignoredEffectFlag, float partialTick, SubmitNodeCollector collector
   ) {
      return !ClientContext.shaderRegistry.renderingIntoTarget;
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = Shift.BEFORE),
      require = 0
   )
   private void beforeScreenRendering(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback, @Local GuiGraphicsExtractor graphics
   ) {
      NameProtectModule.replacementBypassed = true;
      boolean manageProjection;
      if (this.minecraft.gui.screen() instanceof ClientScreen clientScreen) {
         if (clientScreen.hasMenuParent()) {
            clientScreen.getParentScreen()
               .extractRenderStateWithTooltipAndSubtitles(
                  graphics,
                  -99,
                  -99,
                  RenderUtils.getPartialTick()
               );
         }
         manageProjection = false;
      } else if (this.minecraft.gui.screen() != null) {
         manageProjection = true;
      } else {
         return;
      }
      this.guiRenderer.render();
      RenderUtils.clearDepth(this.minecraft.gameRenderer.mainRenderTarget());
      if (!manageProjection) {
         GuiProjectionUtils.beginGuiProjection();
      }
      this.renderClientHud(graphics, manageProjection);
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = Shift.AFTER),
      require = 0
   )
   private void afterScreenRendering(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      NameProtectModule.replacementBypassed = false;
      if (this.minecraft.gui.screen() instanceof ClientScreen) {
         GuiProjectionUtils.restoreProjection();
      }
   }

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0),
      require = 0
   )
   private Overlay captureHudGraphics(
      Overlay overlay, @Local GuiGraphicsExtractor graphics
   ) {
      this.hudGraphics = graphics;
      PoseStack poses = GuiProjectionUtils.toPoseStack(graphics);
      if (ClientAccess.collectiveMinecraft.level != null
         && !ClientAccess.collectiveMinecraft.gui.hud.isHidden()
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

   @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = Shift.AFTER))
   private void renderClientHudAndScreens(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      RenderUtils.clearDepth(this.minecraft.gameRenderer.mainRenderTarget());
      if (ClientAccess.collectiveMinecraft.level != null
         && !ClientAccess.collectiveMinecraft.gui.hud.isHidden()
         && !ClientAccess.collectiveMinecraft.debugEntries.isOverlayVisible()) {
         GuiProjectionUtils.beginGuiProjection();
         PoseStack beforePoses = new PoseStack();
         HudRenderEvent.Before beforeEvent = HudRenderEvent.Before.create(
            beforePoses, this.hudGraphics, RenderUtils.getPartialTick()
         );
         ClientAccess.eventBus.post(beforeEvent);
         GuiDrawUtils.flush();
         GuiProjectionUtils.restoreProjection();
      }
      this.renderClientHud(this.hudGraphics, true);
      if (this.minecraft.gui.screen() instanceof ClientScreen clientScreen) {
         GuiProjectionUtils.beginGuiProjection();
         clientScreen.renderDirect();
         GuiDrawUtils.flush();
         GuiProjectionUtils.restoreProjection();
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void updateFrameTimingAndChatHeight(
      DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback
   ) {
      hud().updateChatInputHeight(
         ClientAccess.collectiveMinecraft.gui.screen() instanceof ChatScreen
            ? 12.0F
            : 0.0F
      );
      ClientContext.frameTiming.recordFrame();
   }

   @Unique
   public void renderClientHud(GuiGraphicsExtractor graphics, boolean manageProjection) {
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

      PoseStack poses = graphics != null ? GuiProjectionUtils.toPoseStack(graphics) : new PoseStack();
      if (!ClientAccess.collectiveMinecraft.gui.hud.isHidden()
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
