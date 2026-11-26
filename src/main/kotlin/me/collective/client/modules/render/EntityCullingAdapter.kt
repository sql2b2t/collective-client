package me.collective.client.modules.render

import dev.tr7zw.entityculling.EntityCullingModBase
import dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase

class EntityCullingAdapter {

   fun isTickCullingEnabled(): Boolean {
      return EntityCullingModBase.instance.config.tickCulling
   }

   fun isCullingEnabled(): Boolean {
      return EntityCullingVersionlessBase.enabled
   }

   fun setCullingEnabled(enabled: Boolean) {
      EntityCullingVersionlessBase.enabled = enabled
   }

   fun setTickCullingEnabled(enabled: Boolean) {
      EntityCullingModBase.instance.config.tickCulling = enabled
   }
}
