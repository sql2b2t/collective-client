package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.PotionItem

class NoHitDelayModule : Module("NoHitDelay", "Removes the weapon attack delay. \n${ChatFormatting.RED}Only works on pre 1.9 servers.", ModuleCategory.COMBAT) {
    private val useKeyTimer = ElapsedTimer()
    private var wasUseKeyDown = false

    @EventHandler
    private fun onClientTick(event: MinecraftRunTickEvent) {
        val useKey = ClientAccess.collectiveMinecraft.options.keyUse
        val useKeyDown = useKey.isDown
        if (useKeyDown && !this.wasUseKeyDown) {
            this.useKeyTimer.reset()
        }
        this.wasUseKeyDown = useKeyDown
    }

    fun hasUseKeyDelayElapsed(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val offhandStack = player.offhandItem
        val useKey = ClientAccess.collectiveMinecraft.options.keyUse
        if (!useKey.isDown) {
            return false
        }

        if (offhandStack.`is`(Items.CROSSBOW)) {
            return false
        }

        var delayMillis = 25
        if (offhandStack.has(DataComponents.FOOD) || offhandStack.item is PotionItem) {
            delayMillis = 50
        }

        return this.useKeyTimer.hasElapsed(delayMillis.toLong())
    }
}
