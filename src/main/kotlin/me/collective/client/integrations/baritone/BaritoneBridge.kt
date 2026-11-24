package me.collective.client.integrations.baritone

import me.collective.client.NamedFeature

import net.minecraft.core.BlockPos

interface BaritoneBridge {
   fun pathElytraTo(position: BlockPos)

   fun setGoal(position: BlockPos)

   fun isPathingOrCalculating(): Boolean

   fun isInSelection(position: BlockPos): Boolean

   fun isPauseRequested(): Boolean

   fun getGoalPosition(): BlockPos?

   fun pathTo(position: BlockPos)

   fun cancelPathing()

   fun isAvailable(): Boolean

   fun requestPause(feature: NamedFeature)

   fun releasePause(feature: NamedFeature)

   fun pathForward()
}
