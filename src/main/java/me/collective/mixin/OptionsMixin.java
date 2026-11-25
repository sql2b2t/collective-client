package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.client.ClientContext;
import me.collective.client.modules.render.ViewClipModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin {
   private static ViewClipModule viewClip;
   private static ViewClipModule viewClip() {
      if (viewClip == null) {
         viewClip = ClientContext.getModule(ViewClipModule.class);
      }
      return viewClip;
   }

   @Shadow
   public abstract void setCameraType(CameraType cameraType);

   @ModifyExpressionValue(method = "<init>", at = @At(value = "CONSTANT", args = "intValue=110"))
   private int replaceFovUpperBound(int originalUpperBound) {
      return 150;
   }

   @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
   public void skipConfiguredCameraType(CameraType cameraType, CallbackInfo callback) {
      if (viewClip().shouldSkipCameraType(cameraType)) {
         CameraType[] cameraTypes = CameraType.values();
         this.setCameraType(cameraTypes[(cameraType.ordinal() + 1) % cameraTypes.length]);
         callback.cancel();
      }
   }
}
