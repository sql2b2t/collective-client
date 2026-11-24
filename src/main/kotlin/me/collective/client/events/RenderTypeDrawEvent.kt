package me.collective.client.events

import com.mojang.blaze3d.textures.GpuTextureView

class RenderTypeDrawEvent(val stage: EventStage) : CancellableEvent() {
    var override: GpuTextureView? = null
}
