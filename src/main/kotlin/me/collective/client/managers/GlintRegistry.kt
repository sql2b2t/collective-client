package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientContext
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.RegistrySetSetting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item

open class GlintRegistry : JsonConfigurable {
   private val neverItems: RegistrySetSetting<Item> = RegistrySetSetting("Never", BuiltInRegistries.ITEM)
   private val alwaysItems: RegistrySetSetting<Item> = RegistrySetSetting("Always", BuiltInRegistries.ITEM)

   override fun fromJson(element: JsonElement) {
      if (element is JsonObject) {
         if (element.has("never")) {
            this.neverItems.fromJson(element.get("never"))
         }
         if (element.has("always")) {
            this.alwaysItems.fromJson(element.get("always"))
         }
      }
   }

   override fun toJson(): JsonElement {
      val json = JsonObject()
      json.add("never", this.neverItems.toJson())
      json.add("always", this.alwaysItems.toJson())
      return json
   }

   override fun getConfigName(): String = "glint.json"

   @Suppress("UNCHECKED_CAST")
   fun toggle(item: Item, mode: GlintMode): Boolean {
      val selectedItems = this.getItems(mode)
      val wasSelected = selectedItems.getValue().contains(item)
      (this.neverItems.getValue() as MutableSet<Item>).removeIf { configuredItem -> configuredItem == item }
      (this.alwaysItems.getValue() as MutableSet<Item>).removeIf { configuredItem -> configuredItem == item }
      if (!wasSelected) {
         (this.getItems(mode).getValue() as MutableSet<Item>).add(item)
      }
      return !wasSelected
   }

   fun getItems(mode: GlintMode): RegistrySetSetting<Item> {
      return if (mode == GlintMode.ALWAYS) this.alwaysItems else this.neverItems
   }

   enum class GlintMode(private val entryName: String) : NamedEntry {
      NEVER("never"),
      ALWAYS("always");

      override fun getName(): String = this.entryName
   }

   companion object {
      @JvmStatic
      fun getOverride(item: Item): GlintMode? {
         val registry = ClientContext.glintRegistry ?: return null
         if (registry.neverItems.getValue().contains(item)) {
            return GlintMode.NEVER
         }
         return if (registry.alwaysItems.getValue().contains(item)) GlintMode.ALWAYS else null
      }
   }
}
