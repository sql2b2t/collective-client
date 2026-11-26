package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.client.modules.render.AmbienceModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.managers.ModuleRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
   private static AmbienceModule ambience;
   private static AmbienceModule ambience() {
      if (ambience == null) {
         ambience = ClientContext.getModule(AmbienceModule.class);
      }
      return ambience;
   }

   @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
   private void overrideRainLevel(CallbackInfoReturnable<Float> callback) {
      if (ambience().worldWeatherSetting.getValue()
         && ambience().isToggled()
         && !ambience().isWeatherOverrideSuppressed()) {
         callback.setReturnValue(ambience().weatherSetting.getValue() == AmbienceModule.WeatherChoice.CLEAR ? 0.0F : 1.0F);
         callback.cancel();
      }
   }

   @WrapOperation(method = "isRaining", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"))
   private float readRainLevelForRainCheck(
      Level level, float partialTick, Operation original
   ) {
      if (ambience().worldWeatherSetting.getValue() && ambience().isToggled()) {
         return (Float)ambience().withWeatherOverrideSuppressed(
            () -> (Float)original.call(level, partialTick)
         );
      }

      return (Float)original.call(level, partialTick);
   }

   @ModifyExpressionValue(
      method = "<init>",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"),
      require = 0
   )
   private RandomSource useThreadSafeRandomSource(RandomSource originalRandom) {
      return RandomSource.createThreadSafe();
   }

   @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
   private void cancelNullEntityTick(
      Consumer ticker, Entity entity, CallbackInfo callback
   ) {
      if (entity == null) {
         callback.cancel();
      }
   }

   @Unique
   private static void collective$bindLevelModules(ModuleRegistry moduleRegistry) {
      ambience = (AmbienceModule)moduleRegistry.getModule(AmbienceModule.class);
   }
}
