package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.SkyboxModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
   private static NoRenderModule noRenderModule;
   private static SkyboxModule skyboxModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static SkyboxModule skyboxModule() {
      if (skyboxModule == null) {
         skyboxModule = ClientContext.getModule(SkyboxModule.class);
      }
      return skyboxModule;
   }

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressBossBars(GuiGraphicsExtractor graphics, CallbackInfo callback) {
      if (noRenderModule().isToggled()
         && noRenderModule().bossBarsSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
   private void suppressBossBarDarkening(CallbackInfoReturnable<Boolean> callback) {
      if (noRenderModule().isToggled()
            && noRenderModule().bossBarsSetting.getValue()
         || skyboxModule().isToggled()
            && skyboxModule().isCurrentDimensionEnabled()) {
         callback.setReturnValue(false);
         callback.cancel();
      }
   }
}
