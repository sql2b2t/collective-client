package me.collective.mixin;

import me.collective.client.modules.misc.AntiQuitModule;
import me.collective.client.ClientContext;
import me.collective.client.gui.screens.DisconnectConfirmationScreen;
import me.collective.client.modules.exploit.IllegalDisconnectModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
   private static AntiQuitModule antiQuitModule;
   private static IllegalDisconnectModule illegalDisconnectModule;
   private static AntiQuitModule antiQuitModule() {
      if (antiQuitModule == null) {
         antiQuitModule = ClientContext.getModule(AntiQuitModule.class);
      }
      return antiQuitModule;
   }
   private static IllegalDisconnectModule illegalDisconnectModule() {
      if (illegalDisconnectModule == null) {
         illegalDisconnectModule = ClientContext.getModule(IllegalDisconnectModule.class);
      }
      return illegalDisconnectModule;
   }
   @Unique
   private boolean disconnectConfirmed;

   protected PauseScreenMixin(Component title) {
      super(title);
   }

   @Shadow
   private void onDisconnect() {
   }

   @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
   private void confirmOrInterceptDisconnect(CallbackInfo callback) {
      Minecraft client = Minecraft.getInstance();
      if (antiQuitModule().isToggled()
         && antiQuitModule().disconnectSetting.getValue()
         && !disconnectConfirmed) {
         client.setScreen(new DisconnectConfirmationScreen(() -> {
            disconnectConfirmed = true;
            client.getReportingContext().draftReportHandled(client, this, this::onDisconnect, true);
         }));
         callback.cancel();
      } else {
         if (illegalDisconnectModule().isToggled()
            && client.player != null
            && !client.isLocalServer()) {
            illegalDisconnectModule().requestDisconnect();
            callback.cancel();
         }
      }
   }
}
