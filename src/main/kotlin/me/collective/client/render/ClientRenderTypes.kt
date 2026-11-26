package me.collective.client.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.TextureTransform
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier
import net.minecraft.util.Util

object ClientRenderTypes {
    private val SHINE_GLINT: RenderType = RenderType.create(
        "collective_shine_glint",
        RenderSetup.builder(ClientRenderPipelines.SHINE_GLINT)
            .withTexture("Sampler0", Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png"))
            .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    )

    private val NAMETAG_ITEM: Function<RenderType, RenderType> = Util.memoize(
        Function<RenderType, RenderType> { original ->
            val state = original.state
            val pipeline = ClientRenderPipelines.getNameTagItemPipeline(state.pipeline)
                ?: return@Function original
            RenderType.create(
                "collective_nametag_item",
                RenderSetup(
                    pipeline, state.textures, state.useLightmap, state.useOverlay,
                    state.layeringTransform, OutputTarget.MAIN_TARGET, state.textureTransform,
                    RenderSetup.OutlineProperty.NONE, false, state.sortOnUpload
                )
            )
        }
    )

    private val ARMOR_CUTOUT_NO_CULL: Function<Identifier, RenderType> = Util.memoize(
        Function<Identifier, RenderType> { texture ->
            createArmorCutoutNoCull("armor_cutout_no_cull", texture)
        }
    )

    private val ENTITY_SOLID: Function<Identifier, RenderType> = Util.memoize(
        Function<Identifier, RenderType> { texture ->
            val renderSetup = RenderSetup.builder(
                ClientRenderPipelines.ENTITY_SOLID_TRANSLUCENT
            )
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
            RenderType.create("entity_solid", renderSetup)
        }
    )

    private val FONT: BiFunction<Identifier, Boolean, RenderType> = Util.memoize(
        BiFunction<Identifier, Boolean, RenderType> { texture, linearFiltering ->
            val renderSetup = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
                .withTexture("Sampler0", texture, textureSampler(linearFiltering))
                .createRenderSetup()
            RenderType.create("collective_font", renderSetup)
        }
    )

    private val IMAGE: BiFunction<Identifier, Boolean, RenderType> = Util.memoize(
        BiFunction<Identifier, Boolean, RenderType> { texture, linearFiltering ->
            val renderSetup = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
                .withTexture("Sampler0", texture, textureSampler(linearFiltering))
                .createRenderSetup()
            RenderType.create("collective_image", renderSetup)
        }
    )

    private val GUI_LINES: RenderType = RenderType.create(
        "gui_lines",
        setup(ClientRenderPipelines.GUI_LINES)
    )

    private val WORLD_FILL: RenderType = RenderType.create(
        "world_fill",
        setup(ClientRenderPipelines.WORLD_FILL)
    )

    private val WORLD_FILL_LIGHTNING: RenderType = create(
        "world_fill_light", ClientRenderPipelines.WORLD_FILL_LIGHTNING
    )

    private val WORLD_FILL_DEPTH: RenderType = RenderType.create(
        "world_fill_depth",
        setup(ClientRenderPipelines.WORLD_FILL_DEPTH)
    )

    private val WORLD_LINES: RenderType = RenderType.create(
        "world_lines",
        setup(ClientRenderPipelines.WORLD_LINES)
    )

    private val WORLD_LINES_DEPTH: RenderType = RenderType.create(
        "world_lines_depth",
        setup(ClientRenderPipelines.WORLD_LINES_DEPTH)
    )

    private val WORLD_LINES_LIGHTNING: RenderType = create(
        "world_fill_light", ClientRenderPipelines.WORLD_LINES_LIGHTNING
    )

    private val WORLD_LINE_STRIP: RenderType = RenderType.create(
        "world_lines_strip",
        setup(ClientRenderPipelines.WORLD_LINE_STRIP)
    )

    private val ENTITY_SHADER: RenderType = RenderType.create(
        "entity_shader",
        setup(ClientRenderPipelines.ENTITY_SHADER)
    )

    private val GUI: RenderType = RenderType.create(
        "gui", setup(RenderPipelines.GUI)
    )

    private val GUI_TRIANGLES: RenderType = RenderType.create(
        "gui_trig",
        setup(ClientRenderPipelines.GUI_TRIANGLES)
    )

    private val GUI_TRIANGLE_STRIP: RenderType = RenderType.create(
        "gui_trig_strip",
        setup(ClientRenderPipelines.GUI_TRIANGLE_STRIP)
    )

    private val BLOCK_ATLAS_IMAGE: RenderType = RenderType.create(
        "collective_image",
        RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
            .createRenderSetup()
    )

    @JvmStatic
    fun guiTriangleStrip(): RenderType = GUI_TRIANGLE_STRIP

    @JvmStatic
    fun worldFillDepth(): RenderType = WORLD_FILL_DEPTH

    @JvmStatic
    fun worldLinesDepth(): RenderType = WORLD_LINES_DEPTH

    @JvmStatic
    fun worldLineStrip(): RenderType = WORLD_LINE_STRIP

    @JvmStatic
    fun guiLines(): RenderType = GUI_LINES

    @JvmStatic
    fun entityShader(): RenderType = ENTITY_SHADER

    @JvmStatic
    fun gui(): RenderType = GUI

    @JvmStatic
    fun armorCutoutNoCull(texture: Identifier): RenderType = ARMOR_CUTOUT_NO_CULL.apply(texture)

    @JvmStatic
    fun shineGlint(): RenderType = SHINE_GLINT

    @JvmStatic
    fun nameTagItem(renderType: RenderType): RenderType = NAMETAG_ITEM.apply(renderType)

    private fun create(name: String, pipeline: RenderPipeline): RenderType {
        return RenderType.create(name, setup(pipeline))
    }

    private fun createArmorCutoutNoCull(name: String, texture: Identifier): RenderType {
        val renderSetup = RenderSetup.builder(
            ClientRenderPipelines.ARMOR_CUTOUT_NO_CULL_TRANSLUCENT
        )
            .withTexture("Sampler0", texture)
            .useLightmap()
            .useOverlay()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
        return RenderType.create(name, renderSetup)
    }

    @JvmStatic
    fun font(texture: Identifier, linearFiltering: Boolean): RenderType = FONT.apply(texture, linearFiltering)

    @JvmStatic
    fun image(texture: Identifier, linearFiltering: Boolean): RenderType = IMAGE.apply(texture, linearFiltering)

    @JvmStatic
    fun entitySolid(texture: Identifier): RenderType = ENTITY_SOLID.apply(texture)

    private fun setup(pipeline: RenderPipeline): RenderSetup {
        return RenderSetup.builder(pipeline).createRenderSetup()
    }

    @JvmStatic
    fun worldFillLightning(): RenderType = WORLD_FILL_LIGHTNING

    @JvmStatic
    fun worldLinesLightning(): RenderType = WORLD_LINES_LIGHTNING

    @JvmStatic
    fun guiTriangles(): RenderType = GUI_TRIANGLES

    @JvmStatic
    fun blockAtlasImage(): RenderType = BLOCK_ATLAS_IMAGE

    @JvmStatic
    fun image(texture: Identifier): RenderType = image(texture, true)

    private fun textureSampler(linearFiltering: Boolean): Supplier<GpuSampler> {
        return Supplier {
            if (linearFiltering) {
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            } else {
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            }
        }
    }

    @JvmStatic
    fun worldFill(): RenderType = WORLD_FILL

    @JvmStatic
    fun worldLines(): RenderType = WORLD_LINES
}
