package me.collective.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class ClientRenderTypes {
   private static final RenderType SHINE_GLINT = RenderType.create(
      "collective_shine_glint",
      RenderSetup.builder(ClientRenderPipelines.SHINE_GLINT)
         .withTexture("Sampler0", ItemRenderer.ENCHANTED_GLINT_ARMOR)
         .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
         .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
         .createRenderSetup()
   );

   private static final Function<RenderType, RenderType> NAMETAG_ITEM = Util.memoize(
      original -> {
         RenderSetup state = original.state;
         RenderPipeline pipeline = ClientRenderPipelines.getNameTagItemPipeline(state.pipeline);
         if (pipeline == null) {
            return original;
         }
         return RenderType.create(
            "collective_nametag_item",
            new RenderSetup(
               pipeline, state.textures, state.useLightmap, state.useOverlay,
               state.layeringTransform, OutputTarget.MAIN_TARGET, state.textureTransform,
               RenderSetup.OutlineProperty.NONE, false, state.sortOnUpload, state.bufferSize
            )
         );
      }
   );

   private static final Function<Identifier, RenderType> ARMOR_CUTOUT_NO_CULL = Util.memoize(
      texture -> createArmorCutoutNoCull("armor_cutout_no_cull", texture)
   );
   private static final Function<Identifier, RenderType> ENTITY_SOLID = Util.memoize(
      texture -> {
         RenderSetup renderSetup = RenderSetup.builder(
               ClientRenderPipelines.ENTITY_SOLID_TRANSLUCENT
            )
            .withTexture("Sampler0", texture)
            .useLightmap()
            .useOverlay()
            .createRenderSetup();
         return RenderType.create("entity_solid", renderSetup);
      }
   );
   private static final BiFunction<Identifier, Boolean, RenderType> FONT = Util.memoize(
      (texture, linearFiltering) -> {
         RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .withTexture("Sampler0", texture, textureSampler(linearFiltering))
            .createRenderSetup();
         return RenderType.create("collective_font", renderSetup);
      }
   );
   private static final BiFunction<Identifier, Boolean, RenderType> IMAGE = Util.memoize(
      (texture, linearFiltering) -> {
         RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .withTexture("Sampler0", texture, textureSampler(linearFiltering))
            .createRenderSetup();
         return RenderType.create("collective_image", renderSetup);
      }
   );
   private static final RenderType GUI_LINES = RenderType.create(
      "gui_lines",
      setup(
         ClientRenderPipelines.GUI_LINES
      )
   );
   private static final RenderType WORLD_FILL = RenderType.create(
      "world_fill",
      setup(
         ClientRenderPipelines.WORLD_FILL
      )
   );
   private static final RenderType WORLD_FILL_LIGHTNING = create(
      "world_fill_light", ClientRenderPipelines.WORLD_FILL_LIGHTNING
   );
   private static final RenderType WORLD_FILL_DEPTH = RenderType.create(
      "world_fill_depth",
      setup(
         ClientRenderPipelines.WORLD_FILL_DEPTH
      )
   );
   private static final RenderType WORLD_LINES = RenderType.create(
      "world_lines",
      setup(
         ClientRenderPipelines.WORLD_LINES
      )
   );
   private static final RenderType WORLD_LINES_DEPTH = RenderType.create(
      "world_lines_depth",
      setup(
         ClientRenderPipelines.WORLD_LINES_DEPTH
      )
   );
   private static final RenderType WORLD_LINES_LIGHTNING = create(
      "world_fill_light", ClientRenderPipelines.WORLD_LINES_LIGHTNING
   );
   private static final RenderType WORLD_LINE_STRIP = RenderType.create(
      "world_lines_strip",
      setup(
         ClientRenderPipelines.WORLD_LINE_STRIP
      )
   );
   private static final RenderType ENTITY_SHADER = RenderType.create(
      "entity_shader",
      setup(
         ClientRenderPipelines.ENTITY_SHADER
      )
   );
   private static final RenderType GUI = RenderType.create(
      "gui", setup(RenderPipelines.DEBUG_QUADS)
   );
   private static final RenderType GUI_TRIANGLES = RenderType.create(
      "gui_trig",
      setup(
         ClientRenderPipelines.GUI_TRIANGLES
      )
   );
   private static final RenderType GUI_TRIANGLE_STRIP = RenderType.create(
      "gui_trig_strip",
      setup(
         ClientRenderPipelines.GUI_TRIANGLE_STRIP
      )
   );
   private static final RenderType BLOCK_ATLAS_IMAGE = RenderType.create(
      "collective_image",
      RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
         .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
         .createRenderSetup()
   );

   public static RenderType guiTriangleStrip() {
      return GUI_TRIANGLE_STRIP;
   }

   public static RenderType worldFillDepth() {
      return WORLD_FILL_DEPTH;
   }

   public static RenderType worldLinesDepth() {
      return WORLD_LINES_DEPTH;
   }

   public static RenderType worldLineStrip() {
      return WORLD_LINE_STRIP;
   }

   public static RenderType guiLines() {
      return GUI_LINES;
   }

   public static RenderType entityShader() {
      return ENTITY_SHADER;
   }

   public static RenderType gui() {
      return GUI;
   }

   public static RenderType armorCutoutNoCull(Identifier texture) {
      return ARMOR_CUTOUT_NO_CULL.apply(texture);
   }

   public static RenderType shineGlint() {
      return SHINE_GLINT;
   }

   public static RenderType nameTagItem(RenderType renderType) {
      return NAMETAG_ITEM.apply(renderType);
   }

   private static RenderType create(
      String name, RenderPipeline pipeline
   ) {
      return RenderType.create(name, setup(pipeline));
   }

   private static RenderType createArmorCutoutNoCull(String name, Identifier texture) {
      RenderSetup renderSetup = RenderSetup.builder(
            ClientRenderPipelines.ARMOR_CUTOUT_NO_CULL_TRANSLUCENT
         )
         .withTexture("Sampler0", texture)
         .useLightmap()
         .useOverlay()
         .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
         .createRenderSetup();
      return RenderType.create(name, renderSetup);
   }

   public static RenderType font(Identifier texture, boolean linearFiltering) {
      return FONT.apply(texture, linearFiltering);
   }

   public static RenderType image(Identifier texture, boolean linearFiltering) {
      return IMAGE.apply(texture, linearFiltering);
   }

   public static RenderType entitySolid(Identifier texture) {
      return ENTITY_SOLID.apply(texture);
   }

   private static RenderSetup setup(RenderPipeline pipeline) {
      return RenderSetup.builder(pipeline).createRenderSetup();
   }

   public static RenderType worldFillLightning() {
      return WORLD_FILL_LIGHTNING;
   }

   public static RenderType worldLinesLightning() {
      return WORLD_LINES_LIGHTNING;
   }

   public static RenderType guiTriangles() {
      return GUI_TRIANGLES;
   }

   public static RenderType blockAtlasImage() {
      return BLOCK_ATLAS_IMAGE;
   }

   public static RenderType image(Identifier texture) {
      return image(texture, true);
   }

   private static Supplier<GpuSampler> textureSampler(boolean linearFiltering) {
      return () -> linearFiltering ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR) : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
   }

   public static RenderType worldFill() {
      return WORLD_FILL;
   }

   public static RenderType worldLines() {
      return WORLD_LINES;
   }
}
