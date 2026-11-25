package me.collective.client.render

interface ForcedEntityLayers {
    fun `collective$setForcedLayers`(forced: Boolean)

    fun `collective$areLayersForced`(): Boolean
}
