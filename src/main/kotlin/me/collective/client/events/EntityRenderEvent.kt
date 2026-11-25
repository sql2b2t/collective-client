package me.collective.client.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.Entity

open class EntityRenderEvent : CancellableEvent() {
    var entity: Entity? = null
        protected set

    var poseStack: PoseStack? = null
        protected set

    var bufferSource: MultiBufferSource? = null
        protected set

    class Pre private constructor() : EntityRenderEvent() {
        private fun init(entity: Entity?, poseStack: PoseStack, bufferSource: MultiBufferSource) {
            this.entity = entity
            this.poseStack = poseStack
            this.bufferSource = bufferSource
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = Pre()

            @JvmStatic
            fun create(entity: Entity?, poseStack: PoseStack, bufferSource: MultiBufferSource): Pre {
                INSTANCE.init(entity, poseStack, bufferSource)
                return INSTANCE
            }
        }
    }

    class Post private constructor() : EntityRenderEvent() {
        private fun init(entity: Entity?, poseStack: PoseStack, bufferSource: MultiBufferSource) {
            this.entity = entity
            this.poseStack = poseStack
            this.bufferSource = bufferSource
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = Post()

            @JvmStatic
            fun create(entity: Entity?, poseStack: PoseStack, bufferSource: MultiBufferSource): Post {
                INSTANCE.init(entity, poseStack, bufferSource)
                return INSTANCE
            }
        }
    }
}
