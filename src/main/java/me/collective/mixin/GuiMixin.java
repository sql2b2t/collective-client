package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.ClientContext;
import me.collective.client.render.RenderUtils;
import me.collective.client.modules.render.CrosshairModule;
import me.collective.client.modules.misc.ExtraTabModule;
import me.collective.client.modules.client.HUDModule;
import me.collective.client.modules.combat.NoHitDelayModule;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public class GuiMixin {
   private static ExtraTabModule extraTabModule;
   private static HUDModule hudModule;
   private static NoRenderModule noRenderModule;
   private static CrosshairModule crosshair;
   private static NoHitDelayModule noHitDelayModule;
   private static ExtraTabModule extraTabModule() {
      if (extraTabModule == null) {
         extraTabModule = ClientContext.getModule(ExtraTabModule.class);
      }
      return extraTabModule;
   }
   private static HUDModule hudModule() {
      if (hudModule == null) {
         hudModule = ClientContext.getModule(HUDModule.class);
      }
      return hudModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static CrosshairModule crosshair() {
      if (crosshair == null) {
         crosshair = ClientContext.getModule(CrosshairModule.class);
      }
      return crosshair;
   }
   private static NoHitDelayModule noHitDelayModule() {
      if (noHitDelayModule == null) {
         noHitDelayModule = ClientContext.getModule(NoHitDelayModule.class);
      }
      return noHitDelayModule;
   }
   @Shadow
   @Final
   private PlayerTabOverlay tabList;
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(method = "render", at = @At("HEAD"))
   private void resetHudRenderColor(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
   private void suppressHeldItemTooltip(GuiGraphics graphics, CallbackInfo callback) {
      if (noRenderModule().isToggled()
         && noRenderModule().heldTooltipsSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
   private void suppressEffectIcons(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      if (hudModule().isToggled()
         && hudModule().iconsSetting
               .getValue()
            == HUDModule.EffectIconMode.HIDE) {
         callback.cancel();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void cancelVanillaCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
      if (!crosshair().isToggled()) {
         return;
      }
      if (crosshair().shouldHideVanillaCrosshair()) {
         callback.cancel();
         return;
      }
      float guiScale = this.minecraft.getWindow().getGuiScale();
      int pixelScale = Math.max(1, Math.round(guiScale * crosshair().scaleSetting.getValue() / 2.0F));
      float scale = pixelScale / guiScale;
      int left = (graphics.guiWidth() - 15) / 2;
      int top = (graphics.guiHeight() - 15) / 2;
      float x = Math.round((left + 7.5F) * guiScale - 7.5F * pixelScale) / guiScale;
      float y = Math.round((top + 7.5F) * guiScale - 7.5F * pixelScale) / guiScale;
      Matrix3x2fStack poses = graphics.pose();
      poses.pushMatrix();
      poses.translate(x, y);
      poses.scale(scale, scale);
      poses.translate(-left, -top);
   }

   @Inject(method = "renderCrosshair", at = @At("RETURN"))
   private void restoreCrosshairPose(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
      if (crosshair().isToggled() && !crosshair().shouldHideVanillaCrosshair()) {
         graphics.pose().popMatrix();
      }
   }

   @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
   private boolean showVanillaCrosshairInThirdPerson(boolean firstPerson) {
      return firstPerson || crosshair().isToggled() && !crosshair().shouldHideVanillaCrosshair() && crosshair().thirdPersonSetting.getValue();
   }

   @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
   public Object suppressCrosshairAttackIndicator(Object originalStatus) {
      return noHitDelayModule().isToggled() ? AttackIndicatorStatus.OFF : originalStatus;
   }

   @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
   private void suppressScoreboardSidebar(
      GuiGraphics graphics, Objective objective, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled() && noRenderModule().scoreBoardSetting.getValue()) {
         callback.cancel();
         return;
      }
      graphics.pose().pushMatrix();
      graphics.pose()
         .translate(
            -Math.max(
               hudModule().getSafeX()
                  - 1,
               0
            ),
            0.0F
         );
   }

   @Inject(method = "displayScoreboardSidebar", at = @At("TAIL"), cancellable = true)
   private void restoreScoreboardPose(
      GuiGraphics graphics, Objective objective, CallbackInfo callback
   ) {
      graphics.pose().popMatrix();
   }

   @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
   private void suppressActionBar(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().actionBarSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true)
   private void renderSmoothTabList(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      if (extraTabModule().isToggled()
         && extraTabModule().smoothSetting.getValue()) {
         callback.cancel();
         Scoreboard scoreboard = this.minecraft.level.getScoreboard();
         Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
         Matrix3x2fStack poses = graphics.pose();
         float openProgress = extraTabModule().getOpenProgress();
         if (openProgress != 0.0F) {
            poses.pushMatrix();
            poses.translate(0.0F, (1.0F - openProgress) * -graphics.guiHeight());
            this.tabList.render(graphics, graphics.guiWidth(), scoreboard, objective);
            poses.popMatrix();
         }
      }
   }

   @ModifyArgs(
      method = "renderCameraOverlays",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0)
   )
   private void applyBlindnessOverlayVisibility(Args arguments) {
      if (noRenderModule().isToggled()
         && noRenderModule().blindnessSetting.getValue()) {
         arguments.set(
            2,
            0.0F
         );
      }
   }
}
