package me.collective.client.events

import net.minecraft.world.entity.player.Player

class PlayerTotemEvent(
    val player: Player,
    val popCount: Int,
    val kind: Kind
) : CancellableEvent() {

    val name: String
        get() = player.gameProfile.name

    enum class Kind {
        TOTEM_POP,
        DEATH
    }
}
