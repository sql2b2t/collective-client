package me.collective.client.utils.inventory

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.util.Optional
import java.util.function.Function
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

abstract class ItemRegistryJsonAdapter : JsonDeserializer<Any>, JsonSerializer<Any> {
   protected fun resolveItem(serializedIdentifier: String): Any? {
      val items: DefaultedRegistry<Item> = BuiltInRegistries.ITEM
      val identifier = ResourceLocation.tryParse(serializedIdentifier) ?: return null
      return items.getValue(identifier)
   }

   override fun deserialize(json: JsonElement, targetType: Type, context: JsonDeserializationContext): Any? {
      val serializedIdentifier = json.asString
      return this.resolveItem(serializedIdentifier)
   }

   override fun serialize(value: Any, sourceType: Type, context: JsonSerializationContext): JsonElement {
      val identifier = this.getItemIdentifier(value)
      val toPrimitive = Function<String, JsonElement> { serializedIdentifier -> JsonPrimitive(serializedIdentifier) }
      val element = identifier.map(toPrimitive)
      val nullValue = JsonNull.INSTANCE
      return element.orElse(nullValue)
   }

   protected fun getItemIdentifier(value: Any): Optional<String> {
      val items: Registry<Item> = BuiltInRegistries.ITEM
      val item = value as Item
      val resourceKey = items.getResourceKey(item)
      return resourceKey.map { key ->
         val identifier = key.location()
         identifier.toString()
      }
   }

   class ItemAdapter : ItemRegistryJsonAdapter() {
   }

   class BlockItemAdapter : ItemRegistryJsonAdapter() {
   }
}
