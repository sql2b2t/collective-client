package me.collective.client.bots.control

import com.google.common.hash.HashCode
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import me.collective.client.bots.BotClient
import me.collective.client.bots.world.BotLocalPlayer
import net.minecraft.core.NonNullList
import net.minecraft.core.RegistryAccess.Frozen
import net.minecraft.network.HashedPatchMap.HashGenerator
import net.minecraft.network.HashedStack
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.RegistryOps
import net.minecraft.util.HashOps
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.ArrayList

class BotContainerController(val bot: BotClient) {
    var menu: AbstractContainerMenu? = null
        private set
    var title: Component = Component.empty()
        private set
    private var hashGenerator: HashGenerator? = null
    private var cachedRegistryAccess: Frozen? = null

    val isMenuOpen: Boolean get() = menu != null

    val containerId: Int get() = menu?.containerId ?: 0

    fun tick() {}

    fun handleOpenScreen(packet: ClientboundOpenScreenPacket) {
        val player = bot.worldState.botLocalPlayer ?: return
        val newMenu = packet.type.create(packet.containerId, player.inventory)
        if (newMenu != null) {
            menu = newMenu
            title = packet.title
            player.containerMenu = newMenu
        }
    }

    fun handleContainerClose(containerId: Int) {
        if (menu != null && menu?.containerId == containerId) {
            resetMenu()
        }
    }

    fun handleContainerSetContent(packet: ClientboundContainerSetContentPacket) {
        val currentMenu = menu
        if (currentMenu != null && currentMenu.containerId == packet.containerId) {
            currentMenu.initializeContents(packet.stateId, packet.items, packet.carriedItem)
        }
    }

    fun handleContainerSetSlot(packet: ClientboundContainerSetSlotPacket) {
        val currentMenu = menu
        if (currentMenu != null && currentMenu.containerId == packet.containerId && currentMenu.isValidSlotIndex(packet.slot)) {
            currentMenu.setItem(packet.slot, packet.stateId, packet.item)
        }
    }

    fun closeMenu() {
        val currentMenu = menu ?: return
        bot.sendPacket(ServerboundContainerClosePacket(currentMenu.containerId))
        resetMenu()
    }

    fun clickButton(buttonId: Int) {
        val currentMenu = menu ?: return
        bot.sendPacket(ServerboundContainerButtonClickPacket(currentMenu.containerId, buttonId))
    }

    fun clickSlot(slotIndex: Int, buttonIndex: Int, clickType: ContainerInput) {
        val player = bot.worldState.botLocalPlayer ?: return
        val activeMenu = menu ?: player.inventoryMenu
        val slots: NonNullList<Slot> = activeMenu.slots
        val oldItems = ArrayList<ItemStack>(slots.size)

        for (slot in slots) {
            oldItems.add(slot.item.copy())
        }

        val carriedCopy = activeMenu.carried.copy()
        activeMenu.clicked(slotIndex, buttonIndex, clickType, player)

        val packet: ServerboundContainerClickPacket
        try {
            packet = ServerboundContainerClickPacket(
                activeMenu.containerId,
                activeMenu.stateId,
                slotIndex.toShort(),
                buttonIndex.toByte(),
                clickType,
                computeChangedSlots(slots, oldItems),
                HashedStack.create(activeMenu.carried, getHashGenerator())
            )
        } catch (e: RuntimeException) {
            for (i in slots.indices) {
                slots[i].set(oldItems[i])
            }
            activeMenu.carried = carriedCopy
            throw e
        }

        bot.sendPacket(packet)
    }

    private fun computeChangedSlots(slots: NonNullList<Slot>, oldItems: List<ItemStack>): Int2ObjectMap<HashedStack> {
        val changed: Int2ObjectMap<HashedStack> = Int2ObjectOpenHashMap()
        val gen = getHashGenerator()
        for (i in slots.indices) {
            val item = slots[i].item
            if (!ItemStack.matches(oldItems[i], item)) {
                changed.put(i, HashedStack.create(item, gen))
            }
        }
        return changed
    }

    private fun getHashGenerator(): HashGenerator {
        val registries = bot.worldState.frozenRegistry ?: return HashGenerator { 0 }
        val currentGen = hashGenerator
        if (currentGen == null || cachedRegistryAccess !== registries) {
            val ops = registries.createSerializationContext(HashOps.CRC32C_INSTANCE)
            val newGen = HashGenerator { comp ->
                val result = comp.encodeValue(ops).getOrThrow { IllegalArgumentException("Failed to hash $comp: $it") }
                (result as HashCode).asInt()
            }
            hashGenerator = newGen
            cachedRegistryAccess = registries
            return newGen
        }
        return currentGen
    }

    fun clear() {
        menu = null
        title = Component.empty()
        hashGenerator = null
        cachedRegistryAccess = null
    }

    private fun resetMenu() {
        val player = bot.worldState.botLocalPlayer
        if (player != null) {
            player.containerMenu = player.inventoryMenu
        }
        menu = null
        title = Component.empty()
    }
}
