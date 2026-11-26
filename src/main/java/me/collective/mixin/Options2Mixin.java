package me.collective.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.collective.client.modules.render.AmbienceModule;
import me.collective.client.ClientContext;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/client/Options$2")
public class Options2Mixin {
   private static AmbienceModule ambienceModule;
   private static AmbienceModule ambienceModule() {
      if (ambienceModule == null) {
         ambienceModule = ClientContext.getModule(AmbienceModule.class);
      }
      return ambienceModule;
   }

   // Mojang leaves the option parse error lambda unnamed.
   @WrapWithCondition(
      method = "method_42571(Ljava/lang/String;Lnet/minecraft/client/OptionInstance;Lcom/mojang/serialization/DataResult$Error;)V",
      at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;[Ljava/lang/Object;)V", remap = false)
   )
   private static boolean shouldLogOptionParseError(
      Logger logger, String message, Object[] arguments
   ) {
      return ambienceModule().brightnessSetting.getValue()
         != AmbienceModule.BrightnessChoice.GAMMA;
   }
}
