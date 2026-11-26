package me.collective.client.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphicsExtractor

open class HudRenderEvent : CancellableEvent() {
    var poseStack: PoseStack? = null
        private set
    var guiGraphics: GuiGraphicsExtractor? = null
        private set
    var partialTick: Float = 0f
        private set

    class Before : HudRenderEvent() {
        companion object {
            private val INSTANCE = Before()

            @JvmStatic
            fun create(poses: PoseStack?, graphics: GuiGraphicsExtractor?, partialTick: Float): Before {
                HudRenderEvent.fill(INSTANCE, poses, graphics, partialTick)
                return INSTANCE
            }
        }
    }

    class Unconditional : HudRenderEvent() {
        companion object {
            private val INSTANCE = Unconditional()

            @JvmStatic
            fun create(poses: PoseStack?, graphics: GuiGraphicsExtractor?, partialTick: Float): Unconditional {
                HudRenderEvent.fill(INSTANCE, poses, graphics, partialTick)
                return INSTANCE
            }
        }
    }

    companion object {
        private val INSTANCE = HudRenderEvent()

        @JvmStatic
        fun create(poses: PoseStack?, graphics: GuiGraphicsExtractor?, partialTick: Float): HudRenderEvent =
            fill(INSTANCE, poses, graphics, partialTick)

        internal fun fill(
            event: HudRenderEvent,
            poses: PoseStack?,
            graphics: GuiGraphicsExtractor?,
            partialTick: Float
        ): HudRenderEvent {
            event.poseStack = poses
            event.partialTick = partialTick
            event.guiGraphics = graphics
            event.resetCancellation()
            return event
        }
    }
}
