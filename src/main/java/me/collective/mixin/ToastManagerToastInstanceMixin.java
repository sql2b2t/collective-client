package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/client/gui/components/toasts/ToastManager$ToastInstance")
public class ToastManagerToastInstanceMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   @Final
   @Shadow
   private Toast toast;

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   public void suppressSelectedToast(GuiGraphics graphics, int screenWidth, CallbackInfo callback) {
      if (noRenderModule().isToggled()
         && (
            this.toast.getToken() == SystemToastId.UNSECURE_SERVER_WARNING && noRenderModule().unsecureServerSetting.getValue()
               || this.toast instanceof TutorialToast && noRenderModule().tutorialToastSetting.getValue()
         )) {
         callback.cancel();
      }
   }
}
