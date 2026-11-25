package me.collective.client.events

import com.mojang.blaze3d.vertex.PoseStack

open class WorldRenderEvent : CancellableEvent() {
    var poseStack: PoseStack? = null
        protected set

    var partialTick: Float = 0.0f
        protected set

    class BeforeBufferSource private constructor() : WorldRenderEvent() {
        private fun init(poses: PoseStack, partialTick: Float) {
            this.poseStack = poses
            this.partialTick = partialTick
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = BeforeBufferSource()

            @JvmStatic
            fun create(poses: PoseStack, partialTick: Float): BeforeBufferSource {
                INSTANCE.init(poses, partialTick)
                return INSTANCE
            }
        }
    }

    class BeforeDepthClear private constructor() : WorldRenderEvent() {
        private fun init(poses: PoseStack, partialTick: Float) {
            this.poseStack = poses
            this.partialTick = partialTick
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = BeforeDepthClear()

            @JvmStatic
            fun create(poses: PoseStack, partialTick: Float): BeforeDepthClear {
                INSTANCE.init(poses, partialTick)
                return INSTANCE
            }
        }
    }

    class Overlay private constructor() : WorldRenderEvent() {
        private fun init(poses: PoseStack, partialTick: Float) {
            this.poseStack = poses
            this.partialTick = partialTick
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = Overlay()

            @JvmStatic
            fun create(poses: PoseStack, partialTick: Float): Overlay {
                INSTANCE.init(poses, partialTick)
                return INSTANCE
            }
        }
    }

    class Main private constructor() : WorldRenderEvent() {
        private fun init(poses: PoseStack, partialTick: Float) {
            this.poseStack = poses
            this.partialTick = partialTick
            this.resetCancellation()
        }

        companion object {
            private val INSTANCE = Main()

            @JvmStatic
            fun create(poses: PoseStack, partialTick: Float): Main {
                INSTANCE.init(poses, partialTick)
                return INSTANCE
            }
        }
    }
}
