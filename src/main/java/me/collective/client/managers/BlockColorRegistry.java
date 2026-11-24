package me.collective.client.managers;

import me.collective.client.config.ColorJsonConverter;

import me.collective.client.settings.JsonConfigurable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public final class BlockColorRegistry implements JsonConfigurable {
   private static final ColorJsonConverter COLOR_CODEC;
   private final Map<Block, Color> colors = new HashMap<>();

   public Map<Block, Color> getColors() {
      return this.colors;
   }

   static {
      COLOR_CODEC = new ColorJsonConverter();
   }

   public Color getColor(Block block) {
      return this.colors.get(block);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json instanceof JsonObject root) {
         JsonObject blocksJson = root.getAsJsonObject("blocks");
         blocksJson.asMap().forEach((blockId, colorJson) -> {
            try {
               ColorJsonConverter codec = COLOR_CODEC;
               Color color = codec.fromJson(colorJson);
               DefaultedRegistry<Block> blockRegistry = BuiltInRegistries.BLOCK;
               Identifier identifier = Identifier.parse(blockId);
               Block block = blockRegistry.getValue(identifier);
               this.colors.put(block, color);
            } catch (Exception exception) {
               exception.printStackTrace();
            }
         });
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      JsonObject blocksJson = new JsonObject();
      this.colors.forEach((block, color) -> {
         Registry<Block> blockRegistry = BuiltInRegistries.BLOCK;
         Optional<ResourceKey<Block>> resourceKeyOption = blockRegistry.getResourceKey(block);
         resourceKeyOption.ifPresent(resourceKey -> {
            Identifier identifier = resourceKey.identifier();
            String blockId = identifier.toString();
            ColorJsonConverter codec = COLOR_CODEC;
            JsonElement encodedColor = codec.toJson(color);
            blocksJson.add(blockId, encodedColor);
         });
      });
      root.add("blocks", blocksJson);
      return root;
   }

   @Override
   public String getConfigName() {
      return "colors.json";
   }

   public void putColor(Block block, Color color) {
      if (color == null) {
         this.colors.remove(block);
      } else {
         this.colors.put(block, color);
      }
   }
}
