package me.collective.client.managers;

import me.collective.client.gui.components.ColorSubmitNodeStorage;
import me.collective.client.render.BaseShaderPass;
import me.collective.client.render.BloomMaskPipeline;
import me.collective.client.render.BloomShaderPass;
import me.collective.client.render.BlurMaskPipeline;
import me.collective.client.render.ColorOutlineBufferSource;
import me.collective.client.render.CustomShaderPass;
import me.collective.client.render.DepthOfFieldPipeline;
import me.collective.client.render.DiscardingBufferSource;
import me.collective.client.render.ESPOutlineShaderPass;
import me.collective.client.render.GradientShaderPass;
import me.collective.client.render.GlowyShaderPipeline;
import me.collective.client.render.NoOpOutlineBufferSource;
import me.collective.client.render.RainbowShaderPass;
import me.collective.client.render.ReloadableShaderPass;
import me.collective.client.render.ShaderPass;
import me.collective.client.render.SkyboxShaderPass;
import me.collective.client.render.SolidShaderPass;

import me.collective.client.ClientAccess;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.resources.model.AtlasManager;

public final class ShaderRegistry implements ClientAccess {
   private static final ColorSubmitNodeStorage submitNodes;
   private static FeatureRenderDispatcher featureDispatcher;
   private static ColorOutlineBufferSource bufferSource;
   private final Object2ObjectOpenHashMap<String, ShaderPass> passes = new Object2ObjectOpenHashMap<>();
   public boolean renderingIntoTarget;
   public boolean renderingHands;

   private static FeatureRenderDispatcher getFeatureDispatcher() {
      if (featureDispatcher == null) {
         featureDispatcher = new FeatureRenderDispatcher(
            submitNodes,
            readBlockRenderer(),
            new ColorOutlineBufferSource.SuppliedBufferSource(ShaderRegistry::getBufferSource),
            readAtlasManager(),
            NoOpOutlineBufferSource.INSTANCE,
            DiscardingBufferSource.INSTANCE,
            ClientAccess.collectiveMinecraft.font
         );
      }

      return featureDispatcher;
   }

   private static BlockRenderDispatcher readBlockRenderer() {
      return ClientAccess.collectiveMinecraft.getBlockRenderer();
   }

   private static AtlasManager readAtlasManager() {
      return ClientAccess.collectiveMinecraft.getAtlasManager();
   }

   public static ColorSubmitNodeStorage getSubmitNodes() {
      return submitNodes;
   }

   public static ColorOutlineBufferSource getBufferSource() {
      if (bufferSource == null) {
         bufferSource = new ColorOutlineBufferSource();
      }

      return bufferSource;
   }

   public ShaderRegistry() {

      this.register("outline", new ESPOutlineShaderPass());

      this.register("solid", new SolidShaderPass());

      this.register("rainbow", new RainbowShaderPass());

      this.register("gradient", new GradientShaderPass());

      this.register("bloom", new BloomShaderPass());

      this.register("custom", new CustomShaderPass());

      this.register("sky", new SkyboxShaderPass());

      this.register("dof", new DepthOfFieldPipeline());

      this.register("blur_mask", new BlurMaskPipeline());

      this.register("bloom_mask", new BloomMaskPipeline());

      this.register("glowy", new GlowyShaderPipeline());
   }

   static {
      submitNodes = new ColorSubmitNodeStorage();
   }

   public ShaderPass getPass(String id) {
      return this.passes.get(id);
   }

   public void reloadAll() {
      for (ShaderPass pass : this.passes.values()) {
         if (pass instanceof BaseShaderPass basePass && pass instanceof ReloadableShaderPass reloadable) {
            reloadable.reload(basePass);
         }
      }
   }

   private void register(String id, ShaderPass pass) {
      this.passes.put(id, pass);
   }

   public void render(String id, Runnable draw) {
      ShaderPass pass = this.getPass(id);
      pass.render(draw);
   }

   public static void finishFeatureRendering() {

      FeatureRenderDispatcher dispatcher = getFeatureDispatcher();
      dispatcher.renderAllFeatures();

      ColorSubmitNodeStorage nodes = getSubmitNodes();
      nodes.endFrame();
   }

   public static void setRenderColor(int color) {

      getBufferSource()
         .setColorArgb(color);
      getSubmitNodes()
         .setColorArgb(color);
   }

}
