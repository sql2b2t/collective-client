package me.collective.client.managers;

import me.collective.client.render.RenderState;

import com.mojang.blaze3d.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;

public final class RenderStateManager {
   private static final List<RenderState> registeredStates = new ObjectArrayList<>();
   public static final RenderState BLENDED_SMOOTH_LINES = entering -> {
      if (entering) {
         GlStateManager._enableBlend();
         GL32C.glEnable(GL32C.GL_LINE_SMOOTH);
      } else {
         GL32C.glDisable(GL32C.GL_LINE_SMOOTH);
         GlStateManager._disableBlend();
      }
   };
   public static final RenderState THROUGH_WALLS = entering -> {
      if (entering) {
         GlStateManager._depthMask(false);
         GlStateManager._disableDepthTest();
         GL20C.glDepthRange(0.0, 0.1);
      } else {
         GlStateManager._depthMask(true);
         GlStateManager._enableDepthTest();
         GL20C.glDepthRange(0.0, 1.0);
      }
   };
   public static final RenderState POLYGON_OFFSET = entering -> {
      if (entering) {
         GlStateManager._enablePolygonOffset();
         GlStateManager._polygonOffset(1.0F,-5e+05F);
         GlStateManager._enableBlend();
         GlStateManager._disableCull();
      } else {
         GlStateManager._polygonOffset(1.0F, 500000.0F);
         GlStateManager._disableBlend();
         GlStateManager._enableCull();
         GlStateManager._disablePolygonOffset();
      }
   };
   public static final RenderState POLYGON_OFFSET_THROUGH_WALLS = RenderStateManager.POLYGON_OFFSET
      .then(
         THROUGH_WALLS
      );
   private static boolean active;

   public static void reset() {
      registeredStates.clear();
      active = false;
   }

   public static void runWithStates(
      Runnable draw, RenderState... states
   ) {
      addStates(states);
      try {
         draw.run();
      } finally {
         reset();
      }
   }

   public static void addStates(RenderState... states) {
      registeredStates.addAll(Arrays.asList(states));
      active = true;
   }

   public static void applyRegistered(boolean entering) {
      if (active) {
         for (RenderState state : registeredStates) {
            state.apply(entering);
         }
      }
   }

   public static boolean isActive() {
      return active;
   }
}
