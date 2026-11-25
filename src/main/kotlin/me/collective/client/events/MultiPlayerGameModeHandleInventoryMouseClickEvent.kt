package me.collective.client.events

import net.minecraft.world.inventory.ClickType

open class MultiPlayerGameModeHandleInventoryMouseClickEvent(
    val containerId: Int,
    val slotId: Int,
    val button: Int,
    val clickType: ClickType
) : CancellableEvent() {

    class After(
        containerId: Int,
        slotId: Int,
        button: Int,
        clickType: ClickType
    ) : MultiPlayerGameModeHandleInventoryMouseClickEvent(containerId, slotId, button, clickType)

    class AfterStackHash(
        containerId: Int,
        slotId: Int,
        button: Int,
        clickType: ClickType
    ) : MultiPlayerGameModeHandleInventoryMouseClickEvent(containerId, slotId, button, clickType)

    class BeforeContainerClick : CancellableEvent()
}
