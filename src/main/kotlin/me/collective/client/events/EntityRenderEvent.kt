package me.collective.client.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.world.entity.Entity

open class EntityRenderEvent : CancellableEvent() {
    var entity: Entity? = null
        protected set

    var poseStack: PoseStack? = null
        protected set

    var collector: SubmitNodeCollector? = null
        protected set

    class Pre private constructor() : EntityRenderEvent() {
        private fun init(entity: Entity?, poseStack: PoseStack, collector: SubmitNodeCollector) {
            this.entity = entity
            this.poseStack = poseStack
            this.collector = collector
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = Pre()

            @JvmStatic
            fun create(entity: Entity?, poseStack: PoseStack, collector: SubmitNodeCollector): Pre {
                INSTANCE.init(entity, poseStack, collector)
                return INSTANCE
            }
        }
    }

    class Post private constructor() : EntityRenderEvent() {
        private fun init(entity: Entity?, poseStack: PoseStack, collector: SubmitNodeCollector) {
            this.entity = entity
            this.poseStack = poseStack
            this.collector = collector
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = Post()

            @JvmStatic
            fun create(entity: Entity?, poseStack: PoseStack, collector: SubmitNodeCollector): Post {
                INSTANCE.init(entity, poseStack, collector)
                return INSTANCE
            }
        }
    }
}
