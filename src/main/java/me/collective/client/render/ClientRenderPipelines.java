package me.collective.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderPipelines;

public final class ClientRenderPipelines {
   public static final Snippet POSITION_TEX_COLOR_SNIPPET = RenderPipeline.builder(
         RenderPipelines.GUI_TEXTURED_SNIPPET
      )
      .withVertexShader("core/collective_pos_tex_color")
      .withFragmentShader("core/collective_pos_tex_color")
      .buildSnippet();
   public static final Snippet GUI_LINES_SNIPPET = RenderPipeline.builder(
         RenderPipelines.GUI_SNIPPET
      )
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, Mode.LINES)
      .buildSnippet();
   public static final Snippet WORLD_LINES_SNIPPET = RenderPipeline.builder(
         RenderPipelines.GUI_SNIPPET
      )
      .withVertexShader("core/collective_lines_smooth")
      .withFragmentShader("core/collective_lines_smooth")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, Mode.LINES)
      .withCull(false)
      .buildSnippet();
   public static final Snippet GUI_TRIANGLES_SNIPPET = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET
      )
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLES)
      .buildSnippet();
   public static final Snippet GUI_TRIANGLE_STRIP_SNIPPET = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET
      )
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLE_STRIP)
      .buildSnippet();
   public static final RenderPipeline POSITION_TEX_COLOR = RenderPipeline.builder(
         POSITION_TEX_COLOR_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withLocation("pipeline/collective_pos_tex_color")
      .withBlend(BlendFunction.TRANSLUCENT)
      .build();
   public static final RenderPipeline ENTITY_SOLID_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, Mode.QUADS)
      .withLocation("pipeline/entity_solid_transparent")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withSampler("Sampler1")
      .build();
   public static final RenderPipeline ENTITY_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, Mode.QUADS)
      .withLocation("pipeline/entity_cutout_no_cull_transparent")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withSampler("Sampler1")
      .withCull(false)
      .build();
   public static final RenderPipeline ARMOR_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, Mode.QUADS)
      .withLocation("pipeline/armor_cutout_no_cull_transparent")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withSampler("Sampler1")
      .withCull(false)
      .withBlend(BlendFunction.TRANSLUCENT)
      .build();
   public static final RenderPipeline GUI_LINES = RenderPipeline.builder(
         GUI_LINES_SNIPPET
      )
      .withLocation("pipeline/gui_lines")
      .build();
   public static final RenderPipeline GUI_TRIANGLES = RenderPipeline.builder(
         GUI_TRIANGLES_SNIPPET
      )
      .withLocation("pipeline/gui_trigs")
      .build();
   public static final RenderPipeline GUI_TRIANGLE_STRIP = RenderPipeline.builder(
         GUI_TRIANGLE_STRIP_SNIPPET
      )
      .withLocation("pipeline/gui_trigs")
      .build();
   public static final RenderPipeline WORLD_FILL = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/world_fill_pipeline")
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline WORLD_FILL_LIGHTNING = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/world_fill_light_pipeline")
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.LIGHTNING)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline WORLD_FILL_DEPTH = RenderPipeline.builder(
         RenderPipelines.DEBUG_FILLED_SNIPPET
      )
      .withLocation("pipeline/world_fill_depth_pipeline")
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline WORLD_LINES = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withLocation("pipeline/world_lines_pipeline")
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline WORLD_LINES_LIGHTNING = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withLocation("pipeline/world_lines_light_pipeline")
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.LIGHTNING)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline WORLD_LINES_DEPTH = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withLocation("pipeline/world_lines_pipeline")
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline WORLD_LINE_STRIP = RenderPipeline.builder(
         WORLD_LINES_SNIPPET
      )
      .withVertexShader("core/position_color")
      .withFragmentShader("core/position_color")
      .withLocation("pipeline/world_lines_pipeline")
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.DEBUG_LINE_STRIP)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthWrite(true)
      .build();
   public static final RenderPipeline ENTITY_SHADER = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .withLocation("pipeline/entity_shader_pipeline")
      .withVertexShader("core/position")
      .withFragmentShader("core/position")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .build();
   public static final RenderPipeline BLIT_TRANSLUCENT = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .build();
   public static final RenderPipeline BLIT_LIGHTNING = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.LIGHTNING)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .build();
   public static final RenderPipeline BLIT_PREMULTIPLIED_ALPHA = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .build();
   public static final RenderPipeline BLIT_NO_BLEND = RenderPipeline.builder(

      )
      .withLocation("pipeline/tracy_blit")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/blit_screen")
      .withSampler("InSampler")
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withoutBlend()
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .build();
   public static final RenderPipeline AMBIENCE = RenderPipeline.builder(

      )
      .withLocation("pipeline/collective_ambience")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/collective_lightmap")
      .withUniform("LightmapInfo", UniformType.UNIFORM_BUFFER)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .build();
   public static final RenderPipeline OCCLUSION = RenderPipeline.builder(

      )
      .withLocation("pipeline/collective_occlusion")
      .withVertexShader("core/screenquad")
      .withFragmentShader("core/collective_occlusion")
      .withUniform("LightmapInfo", UniformType.UNIFORM_BUFFER)
      .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .build();
   public static final RenderPipeline SHINE_GLINT = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET,
         RenderPipelines.FOG_SNIPPET,
         RenderPipelines.GLOBALS_SNIPPET
      )
      .withLocation("pipeline/collective_shine_glint")
      .withVertexShader("core/glint")
      .withFragmentShader("core/glint")
      .withSampler("Sampler0")
      .withDepthWrite(false)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.GLINT)
      .withVertexFormat(DefaultVertexFormat.POSITION_TEX, Mode.QUADS)
      .build();
   public static final RenderPipeline NAMETAG_ITEM = RenderPipeline.builder(
         RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET
      )
      .withLocation("pipeline/collective_nametag_item")
      .withVertexShader("core/rendertype_item_entity_translucent_cull")
      .withFragmentShader("core/rendertype_item_entity_translucent_cull")
      .withSampler("Sampler0")
      .withSampler("Sampler2")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, Mode.QUADS)
      .withDepthBias(1.0F, -500000.0F)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_ENTITY = RenderPipeline.builder(
         RenderPipelines.ENTITY_SNIPPET
      )
      .withLocation("pipeline/collective_nametag_item_entity")
      .withShaderDefine("ALPHA_CUTOUT", 0.1F)
      .withSampler("Sampler1")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthBias(1.0F, -500000.0F)
      .build();
   public static final RenderPipeline NAMETAG_ITEM_GLINT = RenderPipeline.builder(
         RenderPipelines.MATRICES_PROJECTION_SNIPPET,
         RenderPipelines.FOG_SNIPPET,
         RenderPipelines.GLOBALS_SNIPPET
      )
      .withLocation("pipeline/collective_nametag_item_glint")
      .withVertexShader("core/glint")
      .withFragmentShader("core/glint")
      .withSampler("Sampler0")
      .withDepthWrite(false)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
      .withBlend(BlendFunction.GLINT)
      .withVertexFormat(DefaultVertexFormat.POSITION_TEX, Mode.QUADS)
      .withDepthBias(1.0F, -500000.0F)
      .build();

   public static boolean isGlint(RenderPipeline pipeline) {
      return pipeline == RenderPipelines.GLINT
         || pipeline == SHINE_GLINT
         || pipeline == NAMETAG_ITEM_GLINT;
   }

   public static RenderPipeline getNameTagItemPipeline(RenderPipeline pipeline) {
      if (pipeline == RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL) {
         return NAMETAG_ITEM;
      }
      if (pipeline == RenderPipelines.GLINT) {
         return NAMETAG_ITEM_GLINT;
      }
      if (pipeline == RenderPipelines.ENTITY_SOLID
         || pipeline == RenderPipelines.ENTITY_CUTOUT
         || pipeline == RenderPipelines.ENTITY_CUTOUT_NO_CULL
         || pipeline == RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET
         || pipeline == RenderPipelines.ENTITY_SMOOTH_CUTOUT
         || pipeline == RenderPipelines.ENTITY_TRANSLUCENT) {
         return NAMETAG_ITEM_ENTITY;
      }
      return null;
   }
}
