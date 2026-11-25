package me.collective.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.util.OptionalDouble
import java.util.function.BiFunction
import java.util.function.Function
import net.minecraft.Util
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.TriState

object ClientRenderTypes {
    private val SHINE_GLINT: RenderType = RenderType.create(
        "collective_shine_glint",
        DefaultVertexFormat.POSITION_TEX,
        Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
            .setTextureState(RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ENTITY, TriState.FALSE, false))
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setTransparencyState(RenderStateShard.GLINT_TRANSPARENCY)
            .setTexturingState(RenderStateShard.ENTITY_GLINT_TEXTURING)
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .createCompositeState(false)
    )

    private val NAMETAG_ITEM_GLINT: RenderType = RenderType.create(
        "collective_nametag_item_glint",
        DefaultVertexFormat.POSITION_TEX,
        Mode.QUADS,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
            .setTextureState(RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, TriState.FALSE, false))
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.EQUAL_DEPTH_TEST)
            .setTransparencyState(RenderStateShard.GLINT_TRANSPARENCY)
            .setTexturingState(RenderStateShard.GLINT_TEXTURING)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(false)
    )

    private val ARMOR_CUTOUT_NO_CULL: Function<ResourceLocation, RenderType> = Util.memoize(
        Function<ResourceLocation, RenderType> { texture ->
            createArmorCutoutNoCull("armor_cutout_no_cull", texture)
        }
    )

    private val ENTITY_SOLID: Function<ResourceLocation, RenderType> = Util.memoize(
        Function<ResourceLocation, RenderType> { texture ->
            RenderType.create(
                "entity_solid",
                DefaultVertexFormat.NEW_ENTITY,
                Mode.QUADS,
                1536,
                true,
                false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true)
            )
        }
    )

    private val FONT: BiFunction<ResourceLocation, Boolean, RenderType> = Util.memoize(
        BiFunction<ResourceLocation, Boolean, RenderType> { texture, linearFiltering ->
            RenderType.create(
                "collective_font",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                Mode.QUADS,
                786432,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_TEXTURE_COLOR_SHADER)
                    .setTextureState(
                        RenderStateShard.TextureStateShard(
                            texture,
                            if (linearFiltering) TriState.TRUE else TriState.FALSE,
                            false
                        )
                    )
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
            )
        }
    )

    private val IMAGE: BiFunction<ResourceLocation, Boolean, RenderType> = Util.memoize(
        BiFunction<ResourceLocation, Boolean, RenderType> { texture, linearFiltering ->
            RenderType.create(
                "collective_image",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                Mode.QUADS,
                786432,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_TEXTURE_COLOR_SHADER)
                    .setTextureState(
                        RenderStateShard.TextureStateShard(
                            texture,
                            if (linearFiltering) TriState.TRUE else TriState.FALSE,
                            false
                        )
                    )
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
            )
        }
    )

    private val GUI_LINES: RenderType = createSimple(
        "gui_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        Mode.LINES,
        RenderStateShard.RENDERTYPE_LINES_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST,
        lineState = RenderStateShard.LineStateShard(OptionalDouble.empty())
    )

    private val WORLD_FILL: RenderType = createSimple(
        "world_fill",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.QUADS,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val WORLD_FILL_LIGHTNING: RenderType = createSimple(
        "world_fill_light",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.QUADS,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.LIGHTNING_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val WORLD_FILL_DEPTH: RenderType = createSimple(
        "world_fill_depth",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.QUADS,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.LEQUAL_DEPTH_TEST
    )

    private val WORLD_LINES: RenderType = createSimple(
        "world_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        Mode.LINES,
        RenderStateShard.RENDERTYPE_LINES_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST,
        lineState = RenderStateShard.LineStateShard(OptionalDouble.empty())
    )

    private val WORLD_LINES_DEPTH: RenderType = createSimple(
        "world_lines_depth",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        Mode.LINES,
        RenderStateShard.RENDERTYPE_LINES_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.LEQUAL_DEPTH_TEST,
        lineState = RenderStateShard.LineStateShard(OptionalDouble.empty())
    )

    private val WORLD_LINES_LIGHTNING: RenderType = createSimple(
        "world_lines_light",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        Mode.LINES,
        RenderStateShard.RENDERTYPE_LINES_SHADER,
        RenderStateShard.LIGHTNING_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST,
        lineState = RenderStateShard.LineStateShard(OptionalDouble.empty())
    )

    private val WORLD_LINE_STRIP: RenderType = createSimple(
        "world_lines_strip",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.DEBUG_LINE_STRIP,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val ENTITY_SHADER: RenderType = createSimple(
        "entity_shader",
        DefaultVertexFormat.POSITION,
        Mode.QUADS,
        RenderStateShard.POSITION_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val GUI: RenderType = createSimple(
        "gui",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.QUADS,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val GUI_TRIANGLES: RenderType = createSimple(
        "gui_trig",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.TRIANGLES,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val GUI_TRIANGLE_STRIP: RenderType = createSimple(
        "gui_trig_strip",
        DefaultVertexFormat.POSITION_COLOR,
        Mode.TRIANGLE_STRIP,
        RenderStateShard.POSITION_COLOR_SHADER,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        RenderStateShard.NO_DEPTH_TEST
    )

    private val BLOCK_ATLAS_IMAGE: RenderType = image(TextureAtlas.LOCATION_BLOCKS, false)

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
    fun armorCutoutNoCull(texture: ResourceLocation): RenderType = ARMOR_CUTOUT_NO_CULL.apply(texture)

    @JvmStatic
    fun shineGlint(): RenderType = SHINE_GLINT

    @JvmStatic
    fun nameTagItem(renderType: RenderType): RenderType {
        if (renderType == RenderType.glint() ||
            renderType == RenderType.glintTranslucent() ||
            renderType == RenderType.entityGlint() ||
            renderType == RenderType.armorEntityGlint()
        ) {
            return NAMETAG_ITEM_GLINT
        }
        return renderType
    }

    private fun createArmorCutoutNoCull(name: String, texture: ResourceLocation): RenderType {
        return RenderType.create(
            name,
            DefaultVertexFormat.NEW_ENTITY,
            Mode.QUADS,
            1536,
            true,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ARMOR_CUTOUT_NO_CULL_SHADER)
                .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true)
        )
    }

    @JvmStatic
    fun font(texture: ResourceLocation, linearFiltering: Boolean): RenderType = FONT.apply(texture, linearFiltering)

    @JvmStatic
    fun image(texture: ResourceLocation, linearFiltering: Boolean): RenderType = IMAGE.apply(texture, linearFiltering)

    @JvmStatic
    fun entitySolid(texture: ResourceLocation): RenderType = ENTITY_SOLID.apply(texture)

    private fun createSimple(
        name: String,
        format: VertexFormat,
        mode: Mode,
        shader: RenderStateShard.ShaderStateShard,
        transparency: RenderStateShard.TransparencyStateShard,
        depthTest: RenderStateShard.DepthTestStateShard,
        lineState: RenderStateShard.LineStateShard? = null
    ): RenderType {
        val builder = RenderType.CompositeState.builder()
            .setShaderState(shader)
            .setTransparencyState(transparency)
            .setDepthTestState(depthTest)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
        if (lineState != null) {
            builder.setLineState(lineState)
        }
        return RenderType.create(
            name,
            format,
            mode,
            1536,
            false,
            false,
            builder.createCompositeState(false)
        )
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
    fun image(texture: ResourceLocation): RenderType = image(texture, true)

    @JvmStatic
    fun worldFill(): RenderType = WORLD_FILL

    @JvmStatic
    fun worldLines(): RenderType = WORLD_LINES
}
