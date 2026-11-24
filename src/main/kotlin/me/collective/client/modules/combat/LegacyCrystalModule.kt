package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.InteractPacketUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.mixin.MinecraftAccessor
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import java.util.HashMap

class LegacyCrystalModule : Module("LegacyCrystal", "Legit auto crystal (crystal aura).", ModuleCategory.COMBAT) {
    private val delaySetting: Setting<Int> = add(NumberSetting("Delay", 150, 0, 250))
    private val optimizerSetting: Setting<Boolean> = add(BooleanSetting("Optimizer", false))
    private val placementTimes = HashMap<BlockPos, Long>()
    private val attackTimer = ElapsedTimer()
    private var attackDelayMillis = 150L

    @EventHandler
    private fun onLocalPlayerTick(event: LocalPlayerPreTickEvent) {
        val now = System.currentTimeMillis()
        synchronized(placementTimes) {
            placementTimes.entries.removeIf { entry ->
                entry.value + 15000L < now
            }
        }

        val hitResult = ClientAccess.collectiveMinecraft.hitResult
        if (hitResult is EntityHitResult) {
            val entity = hitResult.entity
            val delay = attackDelayMillis

            if (attackTimer.hasElapsed(delay) && entity is EndCrystal) {
                if (placementTimes.containsKey(entity.blockPosition().below())) {
                    (ClientAccess.collectiveMinecraft as MinecraftAccessor).attack()
                    val randomDelay = Math.random()
                    val maximumDelay = delaySetting.value
                    attackDelayMillis = (randomDelay * maximumDelay).toLong()
                    attackTimer.reset()
                }
            }
        }
    }

    @EventHandler
    private fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val hit = event.hitResult

        val hitPosition = hit.blockPos
        val abovePosition = hitPosition.above(2)

        val aboveState = level.getBlockState(abovePosition)
        if (aboveState.isAir) {
            val placementPosition = hit.blockPos
            val canPlaceCrystal = BlockInteractionUtils.canPlaceCrystal(placementPosition, false, false, true, false, false, false)

            val mainHand = player.mainHandItem
            if (mainHand.has(DataComponents.WEAPON)) {
                val item = if (canPlaceCrystal) Items.END_CRYSTAL else Items.OBSIDIAN
                val slot = InventoryUtils.findHotbarSlot(item)
                InventoryUtils.selectHotbarSlotForPlacement(slot)
            } else {
                val heldStack = player.mainHandItem
                if (heldStack.`is`(Items.OBSIDIAN) && canPlaceCrystal) {
                    val slot = InventoryUtils.findHotbarSlot(Items.END_CRYSTAL)
                    InventoryUtils.selectHotbarSlot(slot)
                }
            }
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet
        if (packet is ServerboundUseItemOnPacket) {
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null) {
                val heldStack = player.getItemInHand(packet.hand)
                if (heldStack.`is`(Items.END_CRYSTAL)) {
                    val position = packet.hitResult.blockPos
                    placementTimes[position] = System.currentTimeMillis()
                }
            }
        }

        if (packet is ServerboundInteractPacket) {
            if (optimizerSetting.value) {
                if (InteractPacketUtils.getAction(packet) == InteractPacketUtils.Action.ATTACK) {
                    val entity = InteractPacketUtils.getEntity(packet)
                    if (entity is EndCrystal) {
                        entity.setRemoved(RemovalReason.KILLED)
                    }
                }
            }
        }
    }
}
