package me.collective.client.events

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class BlockOutlineRenderEvent(
    var poseStack: PoseStack,
    // Java mixin passes null for the vertex consumer.
    var vertexConsumer: VertexConsumer?,
    var blockPos: BlockPos,
    var blockState: BlockState,
) : CancellableEvent()
