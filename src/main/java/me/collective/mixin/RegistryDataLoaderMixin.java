package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import me.collective.client.CollectiveClient;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {

   @WrapOperation(method = "load(Lnet/minecraft/resources/RegistryDataLoader$LoadingFunction;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;", at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z"))
   private static boolean reportRegistryErrorsAsEmpty(Map<?, ?> errors, Operation<Boolean> original) {
      if (!errors.isEmpty()) {
         CollectiveClient.LOGGER.warn(
            "Suppressing {} registry loading error(s) that would normally abort loading: {}",
            errors.size(),
            errors.keySet()
         );
      }
      return true;
   }
}