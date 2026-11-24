package me.collective.client.render;

import net.minecraft.client.renderer.MultiBufferSource;

public final class BufferSourceUtils {
   private BufferSourceUtils() {
   }

   public static void cleanup(MultiBufferSource source) {
      if (source instanceof BufferSourceCleanup cleanup) {
         cleanup.collective$cleanAll();
      }
   }
}
