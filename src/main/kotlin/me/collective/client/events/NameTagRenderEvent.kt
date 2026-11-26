package me.collective.client.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.entity.Entity

class NameTagRenderEvent(val poseStack: PoseStack, val entity: Entity?) : CancellableEvent()
