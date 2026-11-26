package me.collective.client.render

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity

interface EntityRenderStateAccess {
    fun `collective$getEntity`(): Entity?

    fun `collective$setEntity`(entity: Entity?)

    companion object {
        @JvmStatic
        fun getEntity(renderState: EntityRenderState): Entity? {
            val access = renderState as EntityRenderStateAccess
            return access.`collective$getEntity`()
        }

        @JvmStatic
        fun setEntity(renderState: EntityRenderState, entity: Entity?) {
            val access = renderState as EntityRenderStateAccess
            access.`collective$setEntity`(entity)
        }
    }
}
