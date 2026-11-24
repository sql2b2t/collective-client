package me.collective.client.render;

import me.collective.client.ClientAccess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.collective.mixin.MinecraftAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.commons.io.IOUtils;

public final class RenderTargetUtils implements ClientAccess {
   private static final Deque<RenderTarget> savedMainRenderTargets = new ArrayDeque<>();

   public static void popFramebuffer() {
      ((LevelRendererFramebufferAccess)ClientAccess.collectiveMinecraft.levelRenderer).collective$popFramebuffer();
   }

   public static void restoreRenderTargets() {
      if (savedMainRenderTargets.isEmpty()) {
         return;
      }

      popFramebuffer();
      setMainRenderTarget(
         savedMainRenderTargets.pop()
      );
   }

   private RenderTargetUtils() {
      throw new AssertionError();
   }

   public static void pushFramebuffer(RenderTarget target) {
      ((LevelRendererFramebufferAccess)ClientAccess.collectiveMinecraft.levelRenderer)
         .collective$pushFramebuffer(target);
   }

   public static void overrideRenderTargets(RenderTarget target) {
      savedMainRenderTargets.push(ClientAccess.collectiveMinecraft.getMainRenderTarget());
      pushFramebuffer(target);
      setMainRenderTarget(target);
   }

   public static void setMainRenderTarget(RenderTarget target) {
      ((MinecraftAccessor)ClientAccess.collectiveMinecraft).collective$setFramebuffer(target);
   }

   public static String readShaderSource(String resourcePath) {
      String resourceLocation = "/assets/collective/shaders/" + resourcePath;
      try (InputStream stream = BaseShaderPass.class.getResourceAsStream(resourceLocation)) {
         if (stream == null) {
            throw new IllegalStateException("Missing shader resource: " + resourceLocation);
         }

         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      } catch (IOException failure) {
         throw new IllegalStateException("Failed to read shader resource: " + resourceLocation, failure);
      }
   }
}
