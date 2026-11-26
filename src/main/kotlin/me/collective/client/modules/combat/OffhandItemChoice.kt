package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.exploit.XCarryModule
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.core.NonNullList
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

enum class OffhandItemChoice(private val displayName: String, private val defaultItem: Item) : ClientAccess, NamedEntry {
    Totem("Totem", Items.TOTEM_OF_UNDYING),
    Crystal("Crystal", Items.END_CRYSTAL),
    Gapple("Gapple", Items.GOLDEN_APPLE) {
        override fun getItem(useCrapple: Boolean): Item {
            return if (useCrapple) super.getItem(true)!! else Items.ENCHANTED_GOLDEN_APPLE
        }
    },
    Custom("Custom", Items.SHIELD) {
        override fun getItem(useCrapple: Boolean): Item? {
            return offhandModule.customSetting.value.stream().findAny().orElse(null)
        }
    };

    override fun getName(): String = displayName

    open fun getItem(useCrapple: Boolean): Item? = defaultItem

    fun findContainerSlot(useCrapple: Boolean): Int {
        val itemToFind = getItem(useCrapple) ?: return -1
        val player = ClientAccess.collectiveMinecraft.player ?: return -1

        if (player.offhandItem.`is`(itemToFind)) return -1
        val screen = ClientAccess.collectiveMinecraft.gui.screen()
        if (screen is ShulkerBoxScreen || screen is ContainerScreen) {
            val containerSlots: NonNullList<Slot> = player.containerMenu.slots
            for (slot in containerSlots) {
                val slotStack: ItemStack = slot.item
                if (slotStack.`is`(itemToFind)) return slot.index
            }
        }
        val includeXCarry = xCarryModule.isToggled()
        return InventoryUtils.findMenuSlot(itemToFind, includeXCarry)
    }

    companion object {
        @JvmStatic
        lateinit var xCarryModule: XCarryModule

        @JvmStatic
        lateinit var offhandModule: OffhandModule

        @JvmStatic
        fun bindModuleRegistry(modules: ModuleRegistry) {
            xCarryModule = modules.getModule(XCarryModule::class.java) as XCarryModule
            offhandModule = modules.getModule(OffhandModule::class.java) as OffhandModule
        }
    }
}
