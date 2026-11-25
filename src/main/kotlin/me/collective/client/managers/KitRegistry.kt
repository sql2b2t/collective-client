package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.collective.client.ClientAccess
import me.collective.client.settings.JsonConfigurable
import me.collective.client.utils.inventory.Kit
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

class KitRegistry : Registry<Kit>(), ClientAccess, JsonConfigurable {
    private var currentKitName: String? = null

    fun setCurrentKitName(name: String?) {
        this.currentKitName = name
    }

    override fun fromJson(json: JsonElement) {
        if (!json.isJsonObject) {
            return
        }
        val root = json.asJsonObject
        if (root.has("current")) {
            setCurrentKitName(root.get("current").asString)
        }

        for ((kitName, kitElement) in root.entrySet()) {
            if (!kitElement.isJsonArray) {
                continue
            }
            val kit = Kit(kitName)
            for (itemElement in kitElement.asJsonArray) {
                if (!itemElement.isJsonObject) {
                    continue
                }
                val slotObject = itemElement.asJsonObject
                if (!slotObject.has("slot") || !slotObject.has("item")) {
                    continue
                }
                var descriptor: Kit.ItemDescriptor? = null
                val itemField = slotObject.get("item")
                if (itemField is JsonObject && itemField.has("id")) {
                    descriptor = parseItemDescriptor(itemField)
                }
                if (itemField is JsonPrimitive && itemField.isNumber) {
                    descriptor = Kit.ItemDescriptor(itemField.asInt, null)
                }
                if (descriptor != null) {
                    try {
                        val slot = slotObject.get("slot").asInt
                        kit.itemsBySlot[slot] = descriptor
                    } catch (_: Exception) {
                        // Битый слот в конфиге пропускается, остальные предметы кита загружаются.
                    }
                }
            }
            registerEntry(kit)
        }
    }

    override fun toJson(): JsonElement {
        val root = JsonObject()
        currentKitName?.let { root.addProperty("current", it) }

        for (kit in getRegistry()) {
            val items = JsonArray()
            kit.itemsBySlot.forEach { (slot, descriptor) ->
                val itemObject = JsonObject()
                val item = Item.byId(descriptor.itemId())
                BuiltInRegistries.ITEM.getResourceKey(item).ifPresent { resourceKey ->
                    itemObject.addProperty("id", resourceKey.location().toString())
                }
                descriptor.potionSignature()?.let { potionSignature ->
                    itemObject.addProperty("data", potionSignature)
                }

                val slotObject = JsonObject()
                slotObject.addProperty("slot", slot)
                slotObject.add("item", itemObject)
                items.add(slotObject)
            }
            root.add(kit.getName(), items)
        }
        return root
    }

    override fun getConfigName(): String = "kits.json"

    fun captureInventory(name: String) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        getRegistry().removeIf(Predicate { existingKit -> existingKit.getName().equals(name, ignoreCase = true) })
        val kit = Kit(name)
        val inventoryItems = player.inventory.items
        for ((slot, stack) in inventoryItems.withIndex()) {
            kit.itemsBySlot[slot] = Kit.ItemDescriptor(capturedItemId(stack), getPotionSignature(stack))
        }
        getRegistry().add(kit)
        setCurrentKitName(name)
    }

    companion object {
        @JvmStatic
        fun getPotionSignature(stack: ItemStack): String? {
            val potionContents = stack.get(DataComponents.POTION_CONTENTS) ?: return null
            val effectSignatures = ArrayList<String>()
            potionContents.forEachEffect { effect ->
                val descriptionId = effect.descriptionId
                val displayedAmplifier = effect.amplifier + 1
                effectSignatures.add("$descriptionId $displayedAmplifier")
            }
            effectSignatures.sort()
            return effectSignatures.joinToString(separator = "")
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun getCurrentKit(registry: KitRegistry): Kit {
            val targetName = registry.currentKitName
                ?: return (null as Kit?) as Kit
            return registry.findFirst(Predicate { kit ->
                kit.getName().equals(targetName, ignoreCase = true)
            }).orElse(null)
        }

        private fun parseItemDescriptor(itemObject: JsonObject): Kit.ItemDescriptor? {
            val idElement = itemObject.get("id")
            val loadedItemId: Int = try {
                idElement.asInt
            } catch (_: Exception) {
                // Битый идентификатор из конфига не должен ронять загрузку китов:
                // tryParse возвращает null вместо исключения, неизвестный предмет пропускается.
                val itemIdentifier = try {
                    idElement.asString
                } catch (_: Exception) {
                    return null
                }
                val parsedIdentifier = ResourceLocation.tryParse(itemIdentifier)
                if (parsedIdentifier == null || !BuiltInRegistries.ITEM.containsKey(parsedIdentifier)) {
                    return null
                }
                val resolvedItem = BuiltInRegistries.ITEM.getValue(parsedIdentifier)
                Item.getId(resolvedItem)
            }
            var potionSignature: String? = null
            if (itemObject.has("data")) {
                try {
                    potionSignature = itemObject.get("data").asString
                } catch (_: Exception) {
                }
            }
            return Kit.ItemDescriptor(loadedItemId, potionSignature)
        }

        private fun capturedItemId(stack: ItemStack): Int = Item.getId(stack.item)
    }
}
