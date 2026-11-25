package me.collective.client.integrations.baritone

import me.collective.client.NamedFeature
import net.minecraft.core.BlockPos

class DisabledBaritoneBridge : BaritoneBridge {
   override fun pathElytraTo(position: BlockPos) {
   }

   override fun setGoal(position: BlockPos) {
   }

   override fun isPathingOrCalculating(): Boolean {
      return false
   }

   override fun isInSelection(position: BlockPos): Boolean {
      return false
   }

   override fun isPauseRequested(): Boolean {
      return false
   }

   override fun getGoalPosition(): BlockPos? {
      return null
   }

   override fun pathTo(position: BlockPos) {
   }

   override fun cancelPathing() {
   }

   override fun isAvailable(): Boolean {
      return false
   }

   override fun requestPause(feature: NamedFeature) {
   }

   override fun releasePause(feature: NamedFeature) {
   }

   override fun pathForward() {
   }
}
