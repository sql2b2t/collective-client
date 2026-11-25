package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientAccess;
import me.collective.client.render.BuiltinResourcePack;
import me.collective.client.managers.ConfigurationManager;
import me.collective.client.CollectiveClient;
import java.io.FileInputStream;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPackResourceManager.class)
public class MultiPackResourceManagerMixin {
   @ModifyReturnValue(method = "getResource", at = @At("RETURN"))
   private Optional resolveClientResource(
      Optional originalResource, @Local(argsOnly = true) ResourceLocation resourceId
   ) {
      if (resourceId.getNamespace().equals("collective-mount")) {
         return Optional.of(
            new Resource(
               new BuiltinResourcePack(),
               () -> new FileInputStream(
                  ConfigurationManager.ROOT_DIRECTORY
                     .resolve(resourceId.getPath())
                     .toFile()
               )
            )
         );
      } else if (originalResource.isEmpty()
         && (resourceId.getNamespace().equals("collective") || isBuiltinShaderResource(resourceId))
         && ClientAccess.hasBundledLanguageResource()) {
         String namespace = isBuiltinShaderResource(resourceId) ? "minecraft" : "collective";
         return Optional.of(
            new Resource(
               new BuiltinResourcePack(),
               () -> CollectiveClient.class.getResourceAsStream(
                  "/assets/%s/%s".formatted(namespace, resourceId.getPath())
               )
            )
         );
      } else {
         return originalResource;
      }
   }

   private static boolean isBuiltinShaderResource(ResourceLocation resourceId) {
      String path = resourceId.getPath();
      return path.contains("/blur_mask.") || path.contains("/bloom.");
   }
}
