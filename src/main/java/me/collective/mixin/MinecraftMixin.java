package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.modules.misc.AntiQuitModule;
import me.collective.client.modules.combat.ArrowsModule;
import me.collective.client.modules.combat.AutoClickerModule;
import me.collective.client.modules.combat.AutoCrystalModule;
import me.collective.client.modules.player.AutoEatModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.gui.screens.ConfirmGameCloseScreen;
import me.collective.client.events.ScreenChangeEvent;
import me.collective.client.events.MinecraftTickEvent;
import me.collective.client.events.MinecraftDisconnectEvent;
import me.collective.client.events.MinecraftRunTickEvent;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.exploit.MultiTaskModule;
import me.collective.client.modules.combat.NoHitDelayModule;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.client.UIModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
   private static UIModule uiModule;
   private static MultiTaskModule multiTaskModule;
   private static AutoEatModule autoEatModule;
   private static AntiQuitModule antiQuitModule;
   private static NoHitDelayModule noHitDelayModule;
   private static AutoClickerModule autoClickerModule;
   private static ArrowsModule arrowsModule;
   private static AutoCrystalModule autoCrystalModule;
   private static NoRenderModule noRenderModule;
   private static UIModule uiModule() {
      if (uiModule == null) {
         uiModule = ClientContext.getModule(UIModule.class);
      }
      return uiModule;
   }
   private static MultiTaskModule multiTaskModule() {
      if (multiTaskModule == null) {
         multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
      }
      return multiTaskModule;
   }
   private static AutoEatModule autoEatModule() {
      if (autoEatModule == null) {
         autoEatModule = ClientContext.getModule(AutoEatModule.class);
      }
      return autoEatModule;
   }
   private static AntiQuitModule antiQuitModule() {
      if (antiQuitModule == null) {
         antiQuitModule = ClientContext.getModule(AntiQuitModule.class);
      }
      return antiQuitModule;
   }
   private static NoHitDelayModule noHitDelayModule() {
      if (noHitDelayModule == null) {
         noHitDelayModule = ClientContext.getModule(NoHitDelayModule.class);
      }
      return noHitDelayModule;
   }
   private static AutoClickerModule autoClickerModule() {
      if (autoClickerModule == null) {
         autoClickerModule = ClientContext.getModule(AutoClickerModule.class);
      }
      return autoClickerModule;
   }
   private static ArrowsModule arrowsModule() {
      if (arrowsModule == null) {
         arrowsModule = ClientContext.getModule(ArrowsModule.class);
      }
      return arrowsModule;
   }
   private static AutoCrystalModule autoCrystalModule() {
      if (autoCrystalModule == null) {
         autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
      }
      return autoCrystalModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   @Unique
   private Screen previousScreen;
   @Shadow
   public int missTime;
   @Shadow
   @Nullable
   public Screen screen;
   private static boolean shutdownSaveStarted = false;

   @Inject(method = "startAttack", at = @At("HEAD"))
   public void resetAttackMissTime(CallbackInfoReturnable<Boolean> callback) {
      if (noHitDelayModule().isToggled()
         || autoClickerModule().isToggled()) {
         this.missTime = 0;
      }
   }

   @Inject(method = "destroy", at = @At("HEAD"))
   public void saveConfigurationOnDestroy(CallbackInfo callback) {
      if (!shutdownSaveStarted
         && ClientContext.configurationManager != null) {
         shutdownSaveStarted = true;
         uiModule().disable();
         ClientContext.configurationManager
            .save();
      }
   }

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;close()V"))
   public void postDisconnectEvent(
      Screen screen, boolean ignoredDisconnectFlag, CallbackInfo callback
   ) {
      MinecraftDisconnectEvent event = new MinecraftDisconnectEvent();
      ClientAccess.eventBus
         .post(event);
   }

   @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   public boolean bypassUsingItemForContinueAttack(boolean originalUsingItem) {
      return !multiTaskModule().isToggled() && originalUsingItem;
   }

   @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 0)
   public boolean bypassDestroyingForStartUseItem(boolean originalDestroying) {
      return multiTaskModule().isToggled() ? false : originalDestroying;
   }

   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void postBeforeScreenChange(Screen newScreen, CallbackInfo callback) {
      this.previousScreen = this.screen;
      ScreenChangeEvent.Before event = new ScreenChangeEvent.Before(
         this.previousScreen, newScreen
      );
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(Lnet/minecraft/client/Minecraft;II)V", shift = Shift.AFTER), cancellable = true)
   private void postAfterScreenInit(Screen newScreen, CallbackInfo callback) {
      ScreenChangeEvent.AfterInit event = new ScreenChangeEvent.AfterInit(
         this.previousScreen, newScreen
      );
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void postMinecraftTick(CallbackInfo callback) {
      MinecraftTickEvent.INSTANCE.resetCancellation();
      ClientAccess.eventBus
         .post(
            MinecraftTickEvent.INSTANCE
         );
   }

   @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   private boolean bypassUsingItemForAutoEat(boolean originalUsingItem) {
      return autoEatModule().isEating()
            && autoEatModule().isToggled()
         ? false
         : originalUsingItem;
   }

   @WrapWithCondition(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;stop()V"))
   private boolean confirmGameClose(Minecraft client) {
      if (antiQuitModule().isToggled()
         && antiQuitModule().gameCloseSetting.getValue()) {
         if (!(client.screen instanceof ConfirmGameCloseScreen)) {
            client.setScreen(new ConfirmGameCloseScreen());
         }

         return false;
      } else {
         return true;
      }
   }

   @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = Shift.AFTER))
   private void postMinecraftRunTick(
      boolean renderLevel, CallbackInfo callback, @Local int ticksToRun
   ) {
      ClientAccess.eventBus
         .post(new MinecraftRunTickEvent(ticksToRun >= 1));
   }

   @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   private boolean bypassUsingItemForArrows(boolean originalUsingItem) {
      return arrowsModule().shouldBypassUsingItemCheck()
         ? false
         : originalUsingItem;
   }

   @Inject(method = "startUseItem", at = @At("HEAD"))
   private void completeCrystalCalculationBeforeUse(CallbackInfo callback) {
      autoCrystalModule().completeCalculation();
   }

   @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
   private boolean bypassHandsBusyForStartUseItem(boolean originalHandsBusy) {
      return multiTaskModule().isToggled() ? false : originalHandsBusy;
   }

   @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
   private boolean bypassHandsBusyForStartAttack(boolean originalHandsBusy) {
      return multiTaskModule().isToggled() ? false : originalHandsBusy;
   }

   @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
   private void suppressEntityGlow(
      Entity entity, CallbackInfoReturnable<Boolean> callback
   ) {
      if (noRenderModule().isToggled()
         && noRenderModule().glowSetting.getValue()) {
         callback.setReturnValue(false);
      }
   }


   @Unique
   private static void collective$bindMinecraftModules(ModuleRegistry moduleRegistry) {
      uiModule = (UIModule)moduleRegistry.getModule(
         UIModule.class
      );
      multiTaskModule = (MultiTaskModule)moduleRegistry.getModule(
         MultiTaskModule.class
      );
      autoEatModule = (AutoEatModule)moduleRegistry.getModule(
         AutoEatModule.class
      );
      antiQuitModule = (AntiQuitModule)moduleRegistry.getModule(
         AntiQuitModule.class
      );
      noHitDelayModule = (NoHitDelayModule)moduleRegistry.getModule(
         NoHitDelayModule.class
      );
      autoClickerModule = (AutoClickerModule)moduleRegistry.getModule(
         AutoClickerModule.class
      );
      arrowsModule = (ArrowsModule)moduleRegistry.getModule(
         ArrowsModule.class
      );
      autoCrystalModule = (AutoCrystalModule)moduleRegistry.getModule(
         AutoCrystalModule.class
      );
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(
         NoRenderModule.class
      );
   }
}
