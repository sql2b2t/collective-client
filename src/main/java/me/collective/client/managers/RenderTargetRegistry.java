package me.collective.client.managers;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Supplier;

public final class RenderTargetRegistry implements ClientAccess {
   private static final ObjectArrayList<ScaledTarget> renderTargets = new ObjectArrayList<>();

   private RenderTargetRegistry() {
   }

   public static RenderTarget createRenderTarget(String name) {
      return createRenderTarget(name, 1);
   }

   public static RenderTarget createRenderTarget(String name, int scale) {
      Window window = ClientAccess.collectiveMinecraft.getWindow();
      TextureTarget target = new TextureTarget(name, Math.max(1, window.getWidth() / scale), Math.max(1, window.getHeight() / scale), true);
      renderTargets.add(new ScaledTarget(target, scale));
      return target;
   }

   public static Supplier createLazyRenderTarget(String name) {
      RenderTarget[] cachedTarget = new RenderTarget[1];
      return () -> {
         if (cachedTarget[0] == null) {
            cachedTarget[0] = createRenderTarget(name);
         }

         return cachedTarget[0];
      };
   }

   public static void resizeRenderTargets(int width, int height) {
      renderTargets.forEach(entry -> entry.target.resize(Math.max(1, width / entry.scale), Math.max(1, height / entry.scale)));
   }

   private record ScaledTarget(RenderTarget target, int scale) {}
}
