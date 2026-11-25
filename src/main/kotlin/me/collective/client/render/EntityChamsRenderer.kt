package me.collective.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.awt.Color
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.ModelFeatureRendererRenderModelEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.utils.entity.SidePlayer
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.FrogModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.WitchModel
import net.minecraft.client.model.VillagerModel
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.client.renderer.entity.state.PlayerRenderState
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

object EntityChamsRenderer : ClientAccess {
    @JvmField
    val fillMesh: MeshBuffer = MeshBuffer.create()
        .setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

    @JvmField
    val outlineMesh: MeshBuffer = MeshBuffer.create()
        .setFormat(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)

    @JvmField
    internal var fillColor: Color? = null

    @JvmField
    internal var outlineColor: Color? = null

    private var lineWidth: Float = 0.0f

    internal lateinit var relation: EntityRenderRelation

    private var renderingEntity: Boolean = false

    init {
        ClientAccess.eventBus.subscribeStatic(EntityChamsRenderer::class.java)
    }

    @JvmStatic
    fun isRenderingEntity(): Boolean = renderingEntity

    @JvmStatic
    fun setLineWidth(width: Double) {
        lineWidth = width.toFloat()
    }

    @JvmStatic
    internal fun getLineWidth(): Float {
        if (lineWidth <= 0.0f) {
            lineWidth = 1.0f
        }
        return lineWidth
    }

    private fun setModelPartSkipDraw(part: ModelPart, partName: String, skipDraw: Boolean) {
        if (part.children.isEmpty()) {
            return
        }

        part.children.forEach { (childName, child) ->
            if (childName == partName) {
                child.skipDraw = skipDraw
            }

            setModelPartSkipDraw(child, partName, skipDraw)
        }
    }

    @JvmStatic
    @EventHandler
    private fun onModelFeatureRendererRenderModelEvent(event: ModelFeatureRendererRenderModelEvent) {
        if (!isRenderingEntity()) {
            return
        }

        val skipDraw = event.stage == EventStage.Pre
        val model = event.model
        if (model is VillagerModel) {
            setModelPartSkipDraw(model, "hat_rim", skipDraw)
        }

        if (model is WitchModel) {
            setModelPartSkipDraw(model, "hat", skipDraw)
            setModelPartSkipDraw(model, "hat2", skipDraw)
            setModelPartSkipDraw(model, "hat3", skipDraw)
            setModelPartSkipDraw(model, "hat4", skipDraw)
            setModelPartSkipDraw(model, "hat_rim", skipDraw)
        }

        if (model is FrogModel) {
            setModelPartSkipDraw(model, "left_foot", skipDraw)
            setModelPartSkipDraw(model, "right_foot", skipDraw)
            setModelPartSkipDraw(model, "left_hand", skipDraw)
            setModelPartSkipDraw(model, "right_hand", skipDraw)
        }
    }

    private fun setModelPartSkipDraw(model: EntityModel<*>, partName: String, skipDraw: Boolean) {
        setModelPartSkipDraw(model.root(), partName, skipDraw)
    }

    @JvmStatic
    fun renderEntity(poses: PoseStack, entity: Entity) {
        renderingEntity = true
        try {
            val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera.position
            var partialTick = RenderUtils.getPartialTick()
            if (entity is SidePlayer) {
                partialTick = 1.0f
            }

            val x = Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x) - cameraPosition.x
            val y = Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y) - cameraPosition.y
            val z = Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z) - cameraPosition.z
            @Suppress("UNCHECKED_CAST")
            val renderer = ClientAccess.collectiveMinecraft.entityRenderDispatcher.getRenderer(entity)
                as EntityRenderer<Entity, EntityRenderState>
            val renderState = renderer.createRenderState(entity, partialTick)
            prepareRenderState(renderState)
            val renderOffset = renderer.getRenderOffset(renderState)
            val renderX = x + renderOffset.x()
            val renderY = y + renderOffset.y()
            val renderZ = z + renderOffset.z()
            poses.pushPose()
            try {
                poses.translate(renderX, renderY, renderZ)
                renderer.render(
                    renderState,
                    poses,
                    ChamsBufferSource.INSTANCE,
                    renderer.getPackedLightCoords(entity, partialTick)
                )
            } finally {
                poses.popPose()
            }
        } finally {
            renderingEntity = false
        }
    }

    @JvmStatic
    fun drawMeshes(fillType: RenderType, outlineType: RenderType) {
        fillMesh.draw(fillType)
        outlineMesh.draw(outlineType)
    }

    @JvmStatic
    fun setColors(outlineColor: Color?, fillColor: Color?) {
        this.fillColor = fillColor
        this.outlineColor = outlineColor
    }

    @JvmStatic
    internal fun hasVisibleOutline(): Boolean {
        val color = outlineColor
        return color != null && color.alpha > 0
    }

    @JvmStatic
    internal fun hasVisibleFill(): Boolean {
        val color = fillColor
        return color != null && color.alpha > 0
    }

    @JvmStatic
    @EventHandler(priority = -9999)
    fun onWorldRender(event: WorldRenderEvent.Main) {
        flush()
    }

    private fun prepareRenderState(renderState: EntityRenderState) {
        renderState.displayFireAnimation = false
        renderState.nameTag = null
        renderState.leashState = null
        if (renderState is HumanoidRenderState) {
            renderState.headEquipment = ItemStack.EMPTY
            renderState.chestEquipment = ItemStack.EMPTY
            renderState.legsEquipment = ItemStack.EMPTY
            renderState.feetEquipment = ItemStack.EMPTY
        }

        if (renderState is PlayerRenderState) {
            val forcedLayers = EntityRenderStateAccess.getEntity(renderState) as? ForcedEntityLayers
            val showOuterLayers = !(forcedLayers != null && forcedLayers.`collective$areLayersForced`())
            renderState.showCape = false
            renderState.showHat = showOuterLayers
            renderState.showJacket = showOuterLayers
            renderState.showLeftPants = showOuterLayers
            renderState.showRightPants = showOuterLayers
            renderState.showLeftSleeve = showOuterLayers
            renderState.showRightSleeve = showOuterLayers
            renderState.arrowCount = 0
        }
    }

    @JvmStatic
    fun setRelation(relation: EntityRenderRelation) {
        this.relation = relation
    }

    @JvmStatic
    fun flush() {
        drawMeshes(ClientRenderTypes.worldFill(), ClientRenderTypes.worldLines())
    }
}
