package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import me.collective.client.gui.screens.ClientScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
   @ModifyExpressionValue(method = "getFramerateLimit", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
   private Screen collective$ignoreClientScreenForThrottle(Screen screen) {
      return screen instanceof ClientScreen ? null : screen;
   }
}
