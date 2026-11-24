package me.collective.client.bots.world

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Inventory
import java.util.*

object BotPlayerSynchronizer {

    fun sync(worldState: BotWorldState, playerState: BotPlayerState, player: BotLocalPlayer) {
        syncAbilities(playerState.abilitiesPacket, player)
        syncFood(playerState, player)
        syncInventory(playerState, player)
        syncAttributes(worldState.getAttributes(playerState.entityId), player)
        syncEffects(worldState.getEffects(playerState.entityId), player)
    }

    private fun syncAbilities(packet: ClientboundPlayerAbilitiesPacket?, player: BotLocalPlayer) {
        if (packet != null) {
            val abilities = player.abilities
            abilities.invulnerable = packet.isInvulnerable
            abilities.flying = packet.isFlying
            abilities.mayfly = packet.canFly()
            abilities.instabuild = packet.canInstabuild()
            abilities.flyingSpeed = packet.flyingSpeed
            abilities.walkingSpeed = packet.walkingSpeed
        }
    }

    private fun syncFood(state: BotPlayerState, player: BotLocalPlayer) {
        val food = player.foodData
        food.foodLevel = state.foodLevel
        food.setSaturation(state.saturation)
    }

    private fun syncInventory(state: BotPlayerState, player: BotLocalPlayer) {
        val content = state.containerContentPacket
        if (content != null) {
            player.inventoryMenu.initializeContents(content.stateId, content.items, content.carriedItem)
        }

        for (slot in state.containerSlotPackets.values) {
            if (player.inventoryMenu.isValidSlotIndex(slot.slot)) {
                player.inventoryMenu.setItem(slot.slot, slot.stateId, slot.item)
            }
        }

        for (invSlot in state.playerInventoryPackets.values) {
            player.inventory.setItem(invSlot.slot, invSlot.contents)
        }

        val selected = sanitizeSlotIndex(state.selectedSlot)
        player.inventory.selectedSlot = selected
    }

    private fun syncAttributes(snapshots: List<AttributeSnapshot>?, player: BotLocalPlayer) {
        if (snapshots != null) {
            for (snapshot in snapshots) {
                val instance = player.attributes.getInstance(snapshot.attribute)
                if (instance != null) {
                    instance.baseValue = snapshot.base
                    instance.removeModifiers()
                    for (modifier in snapshot.modifiers) {
                        instance.addTransientModifier(modifier)
                    }
                }
            }
        }
    }

    private fun syncEffects(effects: List<ClientboundUpdateMobEffectPacket>?, player: BotLocalPlayer) {
        if (effects != null) {
            val presentKeys = HashSet<Holder<*>>()
            for (effectPacket in effects) {
                presentKeys.add(effectPacket.effect)
                val instance = MobEffectInstance(
                    effectPacket.effect,
                    effectPacket.effectDurationTicks,
                    effectPacket.effectAmplifier,
                    effectPacket.isEffectAmbient,
                    effectPacket.isEffectVisible,
                    effectPacket.effectShowsIcon()
                )
                player.forceAddEffect(instance, null)
            }

            for (activeKey in java.util.Set.copyOf(player.activeEffectsMap.keys)) {
                if (!presentKeys.contains(activeKey)) {
                    player.removeEffectNoUpdate(activeKey)
                }
            }
        }
    }

    private fun sanitizeSlotIndex(slot: Int): Int {
        val max = Inventory.getSelectionSize()
        return if (slot in 0 until max) slot else 0
    }
}
