package me.collective.mixin;

import me.collective.client.ClientContext;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightEngine.class)
public class SkyLightEngineMixin {
   private static NoRenderModule noRenderModule;
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }

   // Раньше здесь был @Shadow поле chunkSource, унаследованного из LightEngine:
   // Mixin не находил его в целевом классе и ронял APPLY ("@Shadow field chunkSource
   // was not located") — класс SkyLightEngine не грузился, и клиент падал при входе
   // в мир/на сервер. Захватываем getter напрямую из конструкторов.
   @Unique
   private LightChunkGetter chunkSource;

   @Inject(
      method = "<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;)V",
      at = @At("TAIL")
   )
   private void captureChunkSource(LightChunkGetter chunkGetter, CallbackInfo callback) {
      this.chunkSource = chunkGetter;
   }

   @Inject(
      method = "<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;)V",
      at = @At("TAIL")
   )
   private void captureChunkSource(LightChunkGetter chunkGetter, SkyLightSectionStorage storage, CallbackInfo callback) {
      this.chunkSource = chunkGetter;
   }

   @Inject(at = @At("HEAD"), method = "getLowestSourceY", cancellable = true)
   private void suppressSkyLightSource(
      int chunkX, int chunkZ, int blockY, CallbackInfoReturnable<Integer> callback
   ) {
      NoRenderModule module = noRenderModule();
      if (module != null && module.isToggled() && module.skyLightSetting.getValue()) {
         if (this.chunkSource != null && this.chunkSource.getLevel() instanceof Level level && !level.isClientSide()) {
            return;
         }
         callback.setReturnValue(15);
         callback.cancel();
      }
   }
}