package me.collective.client.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines

object ClientRenderPipelines {
    private val DEPTH_LEQUAL_WRITE = DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true)
    private val DEPTH_ALWAYS_WRITE = DepthStencilState(CompareOp.ALWAYS_PASS, true)
    private val DEPTH_ALWAYS_NOWRITE = DepthStencilState(CompareOp.ALWAYS_PASS, false)
    private val DEPTH_NAMETAG_EQUAL = DepthStencilState(CompareOp.EQUAL, false, 1.0f, -500000.0f)
    private val DEPTH_NAMETAG_LEQUAL = DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, 1.0f, -500000.0f)

    private val LAYOUT_SAMPLER0 = BindGroupLayout.builder().withSampler("Sampler0").build()
    private val LAYOUT_SAMPLER1 = BindGroupLayout.builder().withSampler("Sampler1").build()
    private val LAYOUT_SAMPLER0_2 = BindGroupLayout.builder().withSampler("Sampler0").withSampler("Sampler2").build()
    private val LAYOUT_IN_SAMPLER = BindGroupLayout.builder().withSampler("InSampler").build()
    private val LAYOUT_LIGHTMAP = BindGroupLayout.builder().withUniform("LightmapInfo", UniformType.UNIFORM_BUFFER).build()

    @JvmField
    val POSITION_TEX_COLOR_SNIPPET: Snippet = RenderPipeline.builder(
        RenderPipelines.GUI_TEXTURED_SNIPPET
    )
        .withVertexShader("core/collective_pos_tex_color")
        .withFragmentShader("core/collective_pos_tex_color")
        .buildSnippet()

    @JvmField
    val GUI_LINES_SNIPPET: Snippet = RenderPipeline.builder(
        RenderPipelines.GUI_SNIPPET
    )
        .withVertexShader("core/gui")
        .withFragmentShader("core/gui")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .buildSnippet()

    @JvmField
    val WORLD_LINES_SNIPPET: Snippet = RenderPipeline.builder(
        RenderPipelines.GUI_SNIPPET
    )
        .withVertexShader("core/collective_lines_smooth")
        .withFragmentShader("core/collective_lines_smooth")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withCull(false)
        .buildSnippet()

    @JvmField
    val GUI_TRIANGLES_SNIPPET: Snippet = RenderPipeline.builder(
        RenderPipelines.GUI_SNIPPET
    )
        .withVertexShader("core/gui")
        .withFragmentShader("core/gui")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withCull(false)
        .buildSnippet()

    @JvmField
    val GUI_TRIANGLE_STRIP_SNIPPET: Snippet = RenderPipeline.builder(
        RenderPipelines.GUI_SNIPPET
    )
        .withVertexShader("core/gui")
        .withFragmentShader("core/gui")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
        .withCull(false)
        .buildSnippet()

    @JvmField
    val POSITION_TEX_COLOR: RenderPipeline = RenderPipeline.builder(
        POSITION_TEX_COLOR_SNIPPET
    )
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DEPTH_LEQUAL_WRITE)
        .withLocation("pipeline/collective_pos_tex_color")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .build()

    @JvmField
    val ENTITY_SOLID_TRANSLUCENT: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.ENTITY_SNIPPET
    )
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withLocation("pipeline/entity_solid_transparent")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withBindGroupLayout(LAYOUT_SAMPLER1)
        .build()

    @JvmField
    val ENTITY_CUTOUT_NO_CULL_TRANSLUCENT: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.ENTITY_SNIPPET
    )
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withLocation("pipeline/entity_cutout_no_cull_transparent")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withBindGroupLayout(LAYOUT_SAMPLER1)
        .withCull(false)
        .build()

    @JvmField
    val ARMOR_CUTOUT_NO_CULL_TRANSLUCENT: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.ENTITY_SNIPPET
    )
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withLocation("pipeline/armor_cutout_no_cull_transparent")
        .withShaderDefine("ALPHA_CUTOUT", 0.1f)
        .withBindGroupLayout(LAYOUT_SAMPLER1)
        .withCull(false)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .build()

    @JvmField
    val GUI_LINES: RenderPipeline = RenderPipeline.builder(GUI_LINES_SNIPPET)
        .withLocation("pipeline/gui_lines")
        .build()

    @JvmField
    val GUI_TRIANGLES: RenderPipeline = RenderPipeline.builder(GUI_TRIANGLES_SNIPPET)
        .withLocation("pipeline/gui_trigs")
        .build()

    @JvmField
    val GUI_TRIANGLE_STRIP: RenderPipeline = RenderPipeline.builder(GUI_TRIANGLE_STRIP_SNIPPET)
        .withLocation("pipeline/gui_trigs_strip")
        .build()

    @JvmField
    val WORLD_FILL: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.DEBUG_FILLED_SNIPPET
    )
        .withLocation("pipeline/world_fill_pipeline")
        .withDepthStencilState(DEPTH_ALWAYS_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()

    @JvmField
    val WORLD_FILL_LIGHTNING: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.DEBUG_FILLED_SNIPPET
    )
        .withLocation("pipeline/world_fill_light_pipeline")
        .withDepthStencilState(DEPTH_ALWAYS_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withCull(false)
        .build()

    @JvmField
    val WORLD_FILL_DEPTH: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.DEBUG_FILLED_SNIPPET
    )
        .withLocation("pipeline/world_fill_depth_pipeline")
        .withDepthStencilState(DEPTH_LEQUAL_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()

    @JvmField
    val WORLD_LINES: RenderPipeline = RenderPipeline.builder(WORLD_LINES_SNIPPET)
        .withLocation("pipeline/world_lines_pipeline")
        .withDepthStencilState(DEPTH_ALWAYS_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()

    @JvmField
    val WORLD_LINES_LIGHTNING: RenderPipeline = RenderPipeline.builder(WORLD_LINES_SNIPPET)
        .withLocation("pipeline/world_lines_light_pipeline")
        .withDepthStencilState(DEPTH_ALWAYS_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withCull(false)
        .build()

    @JvmField
    val WORLD_LINES_DEPTH: RenderPipeline = RenderPipeline.builder(WORLD_LINES_SNIPPET)
        .withLocation("pipeline/world_lines_depth_pipeline")
        .withDepthStencilState(DEPTH_LEQUAL_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()

    @JvmField
    val WORLD_LINE_STRIP: RenderPipeline = RenderPipeline.builder(WORLD_LINES_SNIPPET)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withLocation("pipeline/world_line_strip_pipeline")
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINE_STRIP)
        .withDepthStencilState(DEPTH_ALWAYS_WRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .build()

    @JvmField
    val ENTITY_SHADER: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.ENTITY_SNIPPET
    )
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withLocation("pipeline/entity_shader_pipeline")
        .withVertexShader("core/position")
        .withFragmentShader("core/position")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .withCull(false)
        .build()

    @JvmField
    val BLIT_TRANSLUCENT: RenderPipeline = RenderPipeline.builder()
        .withLocation("pipeline/tracy_blit_translucent")
        .withVertexShader("core/screenquad")
        .withFragmentShader("core/blit_screen")
        .withBindGroupLayout(LAYOUT_IN_SAMPLER)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build()

    @JvmField
    val BLIT_LIGHTNING: RenderPipeline = RenderPipeline.builder()
        .withLocation("pipeline/tracy_blit_lightning")
        .withVertexShader("core/screenquad")
        .withFragmentShader("core/blit_screen")
        .withBindGroupLayout(LAYOUT_IN_SAMPLER)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build()

    @JvmField
    val BLIT_PREMULTIPLIED_ALPHA: RenderPipeline = RenderPipeline.builder()
        .withLocation("pipeline/tracy_blit_premultiplied")
        .withVertexShader("core/screenquad")
        .withFragmentShader("core/blit_screen")
        .withBindGroupLayout(LAYOUT_IN_SAMPLER)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build()

    @JvmField
    val BLIT_NO_BLEND: RenderPipeline = RenderPipeline.builder()
        .withLocation("pipeline/tracy_blit_noblend")
        .withVertexShader("core/screenquad")
        .withFragmentShader("core/blit_screen")
        .withBindGroupLayout(LAYOUT_IN_SAMPLER)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build()

    @JvmField
    val AMBIENCE: RenderPipeline = RenderPipeline.builder()
        .withLocation("pipeline/collective_ambience")
        .withVertexShader("core/screenquad")
        .withFragmentShader("core/collective_lightmap")
        .withBindGroupLayout(LAYOUT_LIGHTMAP)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .build()

    @JvmField
    val OCCLUSION: RenderPipeline = RenderPipeline.builder()
        .withLocation("pipeline/collective_occlusion")
        .withVertexShader("core/screenquad")
        .withFragmentShader("core/collective_occlusion")
        .withBindGroupLayout(LAYOUT_LIGHTMAP)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .build()

    @JvmField
    val SHINE_GLINT: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.MATRICES_FOG_SNIPPET,
        RenderPipelines.GLOBALS_SNIPPET
    )
        .withLocation("pipeline/collective_shine_glint")
        .withVertexShader("core/glint")
        .withFragmentShader("core/glint")
        .withBindGroupLayout(LAYOUT_SAMPLER0)
        .withDepthStencilState(DEPTH_ALWAYS_NOWRITE)
        .withCull(false)
        .withColorTargetState(ColorTargetState(BlendFunction.GLINT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build()

    @JvmField
    val NAMETAG_ITEM: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET
    )
        .withLocation("pipeline/collective_nametag_item")
        .withVertexShader("core/rendertype_item_entity_translucent_cull")
        .withFragmentShader("core/rendertype_item_entity_translucent_cull")
        .withBindGroupLayout(LAYOUT_SAMPLER0_2)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DEPTH_NAMETAG_LEQUAL)
        .build()

    @JvmField
    val NAMETAG_ITEM_ENTITY: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.ENTITY_SNIPPET
    )
        .withLocation("pipeline/collective_nametag_item_entity")
        .withShaderDefine("ALPHA_CUTOUT", 0.1f)
        .withBindGroupLayout(LAYOUT_SAMPLER1)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(DEPTH_NAMETAG_LEQUAL)
        .build()

    @JvmField
    val NAMETAG_ITEM_GLINT: RenderPipeline = RenderPipeline.builder(
        RenderPipelines.MATRICES_FOG_SNIPPET,
        RenderPipelines.GLOBALS_SNIPPET
    )
        .withLocation("pipeline/collective_nametag_item_glint")
        .withVertexShader("core/glint")
        .withFragmentShader("core/glint")
        .withBindGroupLayout(LAYOUT_SAMPLER0)
        .withDepthStencilState(DEPTH_NAMETAG_EQUAL)
        .withCull(false)
        .withColorTargetState(ColorTargetState(BlendFunction.GLINT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build()

    @JvmStatic
    fun isGlint(pipeline: RenderPipeline?): Boolean {
        return pipeline === RenderPipelines.GLINT ||
            pipeline === SHINE_GLINT ||
            pipeline === NAMETAG_ITEM_GLINT
    }

    @JvmStatic
    fun getNameTagItemPipeline(pipeline: RenderPipeline?): RenderPipeline? {
        if (pipeline === RenderPipelines.ENTITY_TRANSLUCENT_CULL) {
            return NAMETAG_ITEM
        }
        if (pipeline === RenderPipelines.GLINT) {
            return NAMETAG_ITEM_GLINT
        }
        if (pipeline === RenderPipelines.ENTITY_SOLID ||
            pipeline === RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD ||
            pipeline === RenderPipelines.ENTITY_CUTOUT_CULL ||
            pipeline === RenderPipelines.ENTITY_CUTOUT ||
            pipeline === RenderPipelines.ENTITY_CUTOUT_Z_OFFSET ||
            pipeline === RenderPipelines.ENTITY_TRANSLUCENT
        ) {
            return NAMETAG_ITEM_ENTITY
        }
        return null
    }
}
