package me.collective.client.utils.combat

import net.minecraft.world.level.block.Block

data class PendingPlacement(
   @get:JvmName("createdAtMillis") val createdAtMillis: Long,
   @get:JvmName("block") val block: Block?
)
