package me.collective.client.modules.combat

import net.minecraft.world.entity.player.Player

fun interface AntiPhasePlacementPlanner {
    fun findPlacement(target: Player): AntiPhasePlacement?
}
