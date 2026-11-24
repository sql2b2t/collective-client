package me.collective.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import me.collective.client.ClientContext;
import me.collective.client.modules.misc.ExtraScreenshotModule;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
public class ScreenshotMixin {
   private static ExtraScreenshotModule extraScreenshotModule;
   private static ExtraScreenshotModule extraScreenshotModule() {
      if (extraScreenshotModule == null) {
         extraScreenshotModule = ClientContext.getModule(ExtraScreenshotModule.class);
      }
      return extraScreenshotModule;
   }

   // Mojang leaves the screenshot save lambda unnamed.
   @Inject(method = "method_22691(Lcom/mojang/blaze3d/platform/NativeImage;Ljava/io/File;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
   private static void processExtraScreenshot(
      NativeImage image, File directory, Consumer messageConsumer, CallbackInfo callback
   ) {
      if (extraScreenshotModule().isToggled()) {
         boolean deleteFile = extraScreenshotModule().shouldDeleteFile();
         try {
            // Synchronous on purpose: the vanilla lambda writes and closes the
            // NativeImage right after this hook, so async processing would race
            // with writeToFile/close on the off-heap buffer.
            extraScreenshotModule().processScreenshot(
               image
            );
         } finally {
            if (deleteFile) {
               callback.cancel();
            }
         }
      }
   }

   @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static Consumer suppressVanillaScreenshotMessage(Consumer messageConsumer) {
      return extraScreenshotModule().isToggled()
            && !extraScreenshotModule().isModeNone()
         ? ignoredMessage -> {}
         : messageConsumer;
   }
}
