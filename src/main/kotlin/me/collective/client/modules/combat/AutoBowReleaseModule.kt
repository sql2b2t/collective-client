package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.Items

class AutoBowReleaseModule : Module("AutoBowRelease", "Shoots your bow automatically.", ModuleCategory.COMBAT, "bowspam") {
    private val delaySetting: Setting<Int> = add(NumberSetting("Delay", 5, 2, 20))
    private val crossbowSetting: Setting<Boolean> = add(BooleanSetting("Crossbow", false))
    private var releasedDuringUse = false

    @EventHandler
    private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
        if (isWorldUnavailable) {
            return
        }

        val arrows = ClientContext.getModule(ArrowsModule::class.java) as? ArrowsModule
        if (arrows != null && arrows.isAutoShooting()) {
            return
        }

        if (!hasArrowAccess() || ClientAccess.collectiveMinecraft.screen != null) {
            return
        }

        if (crossbowSetting.value && fireFirstChargedCrossbow()) {
            return
        }

        releaseBowIfReady()
    }

    private fun releaseBowIfReady() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val gameMode = ClientAccess.collectiveMinecraft.gameMode ?: return
        val options = ClientAccess.collectiveMinecraft.options

        if (!player.isUsingItem) {
            if (!options.keyUse.isDown) {
                releasedDuringUse = false
            }
        }

        if (!hasArrowAccess()) {
            return
        }

        if (!isBowReadyToRelease()) {
            return
        }

        gameMode.releaseUsingItem(player)
        if (options.keyUse.isDown) {
            val usedHand = player.usedItemHand
            PacketSender.sendUseItem(usedHand)
            PacketSender.sendSwing(InteractionHand.MAIN_HAND)
        }
        releasedDuringUse = true
    }

    private fun fireFirstChargedCrossbow(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false

        for (hand in InteractionHand.values()) {
            val stack = player.getItemInHand(hand)
            if (stack.`is`(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
                PacketSender.sendUseItem(hand)
                PacketSender.sendSwing(InteractionHand.MAIN_HAND)
                return true
            }
        }

        return false
    }

    private fun hasArrowAccess(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player.isCreative) {
            return true
        }

        return InventoryUtils.countItems { candidate -> candidate.item is ArrowItem } > 0
    }

    private fun isBowReadyToRelease(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val releaseTicks = getReleaseTicks()

        if (!player.isUsingItem) {
            return false
        }

        val useTicks = player.ticksUsingItem
        if (useTicks < releaseTicks) {
            return false
        }

        val useStack = player.useItem
        return useStack.`is`(Items.BOW)
    }

    val releaseTicks: Int
        @JvmName("getReleaseTicksProp") get() = getReleaseTicks()

    fun getReleaseTicks(): Int {
        return delaySetting.value + (if (releasedDuringUse) 0 else 1)
    }

    fun getInitialReleaseTicks(): Int {
        return delaySetting.value + 1
    }
}
