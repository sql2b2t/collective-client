package me.collective.mixin;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.SkyboxModule;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevelData.class)
public class ClientLevelClientLevelDataMixin {
   private static SkyboxModule skyboxModule;
   private static SkyboxModule skyboxModule() {
      if (skyboxModule == null) {
         skyboxModule = ClientContext.getModule(SkyboxModule.class);
      }
      return skyboxModule;
   }

   @Inject(method = "getHorizonHeight", at = @At("HEAD"), cancellable = true)
   private void overrideHorizonHeightForSkybox(CallbackInfoReturnable<Double> callback) {
      if (ClientAccess.collectiveMinecraft.level != null) {
         if (skyboxModule().isToggled()
            && skyboxModule().isCurrentDimensionEnabled()
            && !ClientAccess.collectiveMinecraft
               .level
               .dimension()
               .identifier()
               .getPath()
               .contains("over")) {
            callback.setReturnValue(1.0);
            callback.cancel();
         }
      }
   }
}
