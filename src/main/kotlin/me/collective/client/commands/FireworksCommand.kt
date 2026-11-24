package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items

class FireworksCommand : Command("fireworks") {
    private var launchTicksRemaining: Int = 0

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    private fun useRocket() {
        val selectedSlot = InventoryUtils.getSelectedSlot()
        val hotbarRocketSlot = InventoryUtils.findHotbarSlot(Items.FIREWORK_ROCKET)
        val inventoryRocketSlot = InventoryUtils.findMenuSlot(Items.FIREWORK_ROCKET)

        val (rocketSlot, useInventorySwap) = if (hotbarRocketSlot != -1) {
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null && player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND) {
                Pair(inventoryRocketSlot, true)
            } else {
                Pair(hotbarRocketSlot, false)
            }
        } else {
            Pair(inventoryRocketSlot, true)
        }

        withTemporaryRocketSlot(selectedSlot, rocketSlot, useInventorySwap) {
            fireworksModule?.rocketUseTimer?.reset()
            val gameMode = ClientAccess.collectiveMinecraft.gameMode
            val player = ClientAccess.collectiveMinecraft.player
            if (gameMode != null && player != null) {
                gameMode.useItem(player, InteractionHand.MAIN_HAND)
                ClientContext.swingManager.swing(InteractionHand.MAIN_HAND, SwingAction.INTERACT)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        root.then(literal<Any?>("launch").executes {
            launchTicksRemaining = 3
            1
        })
    }

    private fun withTemporaryRocketSlot(
        selectedSlot: Int,
        rocketSlot: Int,
        useInventorySwap: Boolean,
        useRocket: Runnable
    ) {
        if (rocketSlot != -1) {
            if (useInventorySwap) {
                var menuSlot = InventoryUtils.toMenuSlot(rocketSlot)
                InventoryUtils.swapIntoSelectedSlot(menuSlot)
                useRocket.run()

                menuSlot = InventoryUtils.toMenuSlot(rocketSlot)
                InventoryUtils.swapIntoSelectedSlot(menuSlot)
            } else {
                InventoryUtils.selectHotbarSlot(rocketSlot)
                useRocket.run()
                InventoryUtils.selectHotbarSlot(selectedSlot)
            }
        }
    }

    @EventHandler
    private fun onLocalPlayerTick(event: LocalPlayerPreTickEvent) {
        if (launchTicksRemaining > 0) {
            launchTicksRemaining--
            ClientContext.inputOverrides.requestJump(true)
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null && !player.onGround()) {
                useRocket()
                launchTicksRemaining = 0
            }
        }
    }

    companion object {
        private val fireworksModule: FireworksModule? by lazy {
            ClientContext.getModule(FireworksModule::class.java)
        }
    }
}
