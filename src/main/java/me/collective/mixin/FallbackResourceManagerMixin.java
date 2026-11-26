package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.collective.client.ClientAccess;
import me.collective.client.render.BuiltinResourcePack;
import me.collective.client.managers.ConfigurationManager;
import me.collective.client.CollectiveClient;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallbackResourceManager.class)
public class FallbackResourceManagerMixin {
   @ModifyReturnValue(method = "getResource", at = @At("RETURN"))
   private Optional resolveClientResource(
      Optional originalResource, @Local(argsOnly = true) Identifier resourceId
   ) {
      if (resourceId.getNamespace().equals("collective-mount")) {
         Path rootDirectory = ConfigurationManager.ROOT_DIRECTORY.normalize();
         Path resolvedPath = rootDirectory.resolve(resourceId.getPath()).normalize();
         if (!resolvedPath.startsWith(rootDirectory)
            || !Files.isRegularFile(resolvedPath)) {
            return originalResource;
         }
         return Optional.of(
            new Resource(
               new BuiltinResourcePack(),
               () -> new FileInputStream(resolvedPath.toFile())
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

   private static boolean isBuiltinShaderResource(Identifier resourceId) {
      String path = resourceId.getPath();
      return path.contains("/blur_mask.") || path.contains("/bloom.");
   }
}
