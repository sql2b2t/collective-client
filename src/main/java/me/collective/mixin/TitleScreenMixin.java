package me.collective.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.client.HudColorMode;
import me.collective.client.modules.client.HUDModule;
import me.collective.client.commands.PanicCommand;
import me.collective.client.modules.client.UIModule;
import me.collective.client.modules.hud.WatermarkModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
   private static WatermarkModule watermarkModule;
   private static HUDModule hudModule;
   private static UIModule uiModule;
   private static WatermarkModule watermarkModule() {
      if (watermarkModule == null) {
         watermarkModule = ClientContext.getModule(WatermarkModule.class);
      }
      return watermarkModule;
   }
   private static HUDModule hudModule() {
      if (hudModule == null) {
         hudModule = ClientContext.getModule(HUDModule.class);
      }
      return hudModule;
   }
   private static UIModule uiModule() {
      if (uiModule == null) {
         uiModule = ClientContext.getModule(UIModule.class);
      }
      return uiModule;
   }

   protected TitleScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void renderTitleWatermark(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback, @Local(ordinal = 1) float opacity
   ) {
      if (!PanicCommand.unloaded) {
         Minecraft client = ClientAccess.collectiveMinecraft;
         int hudColor = (hudModule().fadeSetting.getValue()).getColor(hudModule(), 0.0F).getRGB();
         String versionText = ChatFormatting.WHITE + watermarkModule().getVersionText();
         graphics.drawString(
            client.font,
            "Collective " + versionText,
            2,
            2,
            ARGB.color(opacity, hudColor)
         );
      }
   }

   @Override
   public boolean keyPressed(KeyEvent keyEvent) {
      if (!uiModule().getKeybind().mouse() && uiModule().getKeybind().code() == keyEvent.key()) {
         uiModule().enable();
         ClientContext.getClickGui().open();
      }

      return super.keyPressed(keyEvent);
   }
}
