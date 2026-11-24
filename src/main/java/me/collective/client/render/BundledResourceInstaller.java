package me.collective.client.render;

import me.collective.client.managers.ConfigurationManager;
import me.collective.client.utils.FileUtils;
import me.collective.client.CollectiveClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

public final class BundledResourceInstaller {
   private final HashMap<String, String> resourcePaths = new HashMap<>();

   public void installMissingResources() {
      this.resourcePaths.forEach(this::installMissingResource);
   }

   public BundledResourceInstaller() {
      this.resourcePaths.put("textures/overlay.png", "assets/collective/textures/overlay.png");
      this.resourcePaths.put("textures/skin_protect.png", "assets/collective/textures/skin_protect.png");
      this.resourcePaths.put("textures/shine.png", "assets/collective/textures/shine.png");
      this.resourcePaths.put("shaders/sky.frag", "assets/collective/shaders/sky/sky.frag");
      this.resourcePaths.put("shaders/custom.frag", "assets/collective/shaders/custom/example.frag");
   }

   private void installMissingResource(String relativeDestination, String bundledResource) {
      Path destinationPath = ConfigurationManager.ROOT_DIRECTORY.resolve(relativeDestination);
      File destinationFile = destinationPath.toFile();
      if (!destinationFile.exists()) {
         try (InputStream input = CollectiveClient.class.getResourceAsStream("/" + bundledResource)) {
            if (input == null) {
               return;
            }
            byte[] resourceBytes = input.readAllBytes();
            FileUtils.writeBytes(destinationPath, resourceBytes);
         } catch (IOException ioFailure) {
            ioFailure.printStackTrace();
         }
      }
   }
}
