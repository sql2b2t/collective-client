package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.awt.Color
import me.collective.client.config.ColorJsonConverter
import me.collective.client.settings.JsonConfigurable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block

class BlockColorRegistry : JsonConfigurable {
   private val colorsMap: MutableMap<Block, Color> = HashMap()

   val colors: MutableMap<Block, Color>
      @JvmName("getColorsProp")
      get() = this.colorsMap

   fun getColors(): MutableMap<Block, Color> = this.colorsMap

   fun getColor(block: Block?): Color? = if (block == null) null else this.colorsMap[block]

   override fun fromJson(json: JsonElement) {
      if (json is JsonObject) {
         val blocksJson = json.getAsJsonObject("blocks") ?: return
         blocksJson.asMap().forEach { (blockId, colorJson) ->
            try {
               val color = COLOR_CODEC.fromJson(colorJson)
               val identifier = Identifier.parse(blockId)
               val block = BuiltInRegistries.BLOCK.getValue(identifier)
               this.colorsMap[block] = color
            } catch (exception: Exception) {
               exception.printStackTrace()
            }
         }
      }
   }

   override fun toJson(): JsonElement {
      val root = JsonObject()
      val blocksJson = JsonObject()
      this.colorsMap.forEach { (block, color) ->
         BuiltInRegistries.BLOCK.getResourceKey(block).ifPresent { resourceKey ->
            val blockId = resourceKey.identifier().toString()
            val encodedColor = COLOR_CODEC.toJson(color)
            blocksJson.add(blockId, encodedColor)
         }
      }
      root.add("blocks", blocksJson)
      return root
   }

   override fun getConfigName(): String = "colors.json"

   fun putColor(block: Block, color: Color?) {
      if (color == null) {
         this.colorsMap.remove(block)
      } else {
         this.colorsMap[block] = color
      }
   }

   companion object {
      private val COLOR_CODEC = ColorJsonConverter()
   }
}
