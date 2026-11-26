package me.collective.client.events

import net.minecraft.client.model.EntityModel

class ModelFeatureRendererRenderModelEvent(
    var model: EntityModel<*>,
    var stage: EventStage
) : CancellableEvent()
