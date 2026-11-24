package me.collective.mixin;

import me.collective.client.render.ColorUtils;
import me.collective.client.utils.chat.ChatUtils;
import java.awt.Color;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessageTag.class)
public class GuiMessageTagMixin {

   @Inject(method = "indicatorColor", at = @At("HEAD"), cancellable = true)
   private void overrideClientMessageIndicatorColor(CallbackInfoReturnable<Integer> callback) {
      if ((Object)this == ChatUtils.MESSAGE_TAG) {
         Color themeColor = ColorUtils.getThemeColor();
         callback.setReturnValue(
            ColorUtils.pulse(
                  themeColor, themeColor.darker(), 3000.0, 0.0
               )
               .hashCode()
         );
         callback.cancel();
      }
   }
}
