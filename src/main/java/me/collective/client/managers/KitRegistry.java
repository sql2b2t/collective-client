package me.collective.client.managers;

import me.collective.client.utils.inventory.Kit;

import me.collective.client.ClientAccess;
import me.collective.client.settings.JsonConfigurable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

public final class KitRegistry extends me.collective.client.managers.Registry<Kit> implements ClientAccess, JsonConfigurable {
   private String currentKitName;

   public void setCurrentKitName(String name) {
      this.currentKitName = name;
   }

   public static String getPotionSignature(ItemStack stack) {
      DataComponentType<PotionContents> potionContentsType = DataComponents.POTION_CONTENTS;
      PotionContents potionContents = stack.get(potionContentsType);
      if (potionContents != null) {
         ArrayList effectSignatures = new ArrayList();
         Consumer effectConsumer = (java.util.function.Consumer<net.minecraft.world.effect.MobEffectInstance>)(effect -> {
            String descriptionId = effect.getDescriptionId();
            int displayedAmplifier = effect.getAmplifier() + 1;
            effectSignatures.add(descriptionId + " " + displayedAmplifier);
         });

         potionContents.forEachEffect(effectConsumer, 1.0F);
         effectSignatures.sort(Comparator.comparing((Function<String, String>)effectSignature -> effectSignature));
         return String.join("", effectSignatures);
      } else {
         return null;
      }
   }

   public static Kit getCurrentKit(KitRegistry registry) {
      if (registry.currentKitName == null) {
         return null;
      }

      Predicate<Kit> currentKitFilter = kit -> kit.getName().equalsIgnoreCase(registry.currentKitName);
      Optional<Kit> matchingKit = registry.findFirst(currentKitFilter);
      return matchingKit.orElse(null);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (!json.isJsonObject()) {
         return;
      }
      JsonObject root = json.getAsJsonObject();
      if (root.has("current")) {
         this.setCurrentKitName(root.get("current").getAsString());
      }

      Iterator kitEntries = root.entrySet().iterator();
      while (kitEntries.hasNext()) {
         Entry kitEntry = (Entry)kitEntries.next();
         JsonElement kitJsonForCheck = (JsonElement)kitEntry.getValue();
         if (!kitJsonForCheck.isJsonArray()) {
            continue;
         }
         Kit kit = new Kit((String)kitEntry.getKey());
         JsonElement kitJson = (JsonElement)kitEntry.getValue();
         JsonArray itemArray = kitJson.getAsJsonArray();
         Iterator items = itemArray.iterator();
         while (items.hasNext()) {
            JsonElement itemJson = (JsonElement)items.next();
            if (!itemJson.isJsonObject()) {
               continue;
            }
            JsonObject slotObject = itemJson.getAsJsonObject();
            if (!slotObject.has("slot") || !slotObject.has("item")) {
               continue;
            }
            Kit.ItemDescriptor descriptor = null;
            if (slotObject.get("item") instanceof JsonObject itemObject) {
               if (itemObject.has("id")) {
                  descriptor = parseItemDescriptor(itemObject);
               }
            }
            if (slotObject.get("item") instanceof JsonPrimitive primitiveItem) {
               if (primitiveItem.isNumber()) {
                  descriptor = new Kit.ItemDescriptor(primitiveItem.getAsInt(), null);
               }
            }
            if (descriptor != null) {
               try {
                  Map itemsBySlot = kit.getItemsBySlot();
                  int slot = slotObject.get("slot").getAsInt();
                  itemsBySlot.put(slot, descriptor);
               } catch (Exception brokenSlotEntry) {
                  // Битый слот в конфиге пропускается, остальные предметы кита загружаются.
               }
            }
         }
         this.registerEntry(kit);
      }
   }

   private static Kit.ItemDescriptor parseItemDescriptor(JsonObject itemObject) {
      JsonElement idElement = itemObject.get("id");
      int loadedItemId;
      try {
         loadedItemId = idElement.getAsInt();
      } catch (Exception nonNumericItemId) {
         // Битый идентификатор из конфига не должен ронять загрузку китов:
         // tryParse возвращает null вместо исключения, неизвестный предмет пропускается.
         String itemIdentifier;
         try {
            itemIdentifier = idElement.getAsString();
         } catch (Exception malformedItemId) {
            return null;
         }
         Identifier parsedIdentifier = Identifier.tryParse(itemIdentifier);
         if (parsedIdentifier == null || !BuiltInRegistries.ITEM.containsKey(parsedIdentifier)) {
            return null;
         }
         Item resolvedItem = (Item)BuiltInRegistries.ITEM.getValue(parsedIdentifier);
         loadedItemId = Item.getId(resolvedItem);
      }
      String potionSignature = null;
      if (itemObject.has("data")) {
         try {
            potionSignature = itemObject.get("data").getAsString();
         } catch (Exception malformedSignature) {
         }
      }
      return new Kit.ItemDescriptor(loadedItemId, potionSignature);
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      if (this.currentKitName != null) {
         String currentName = this.currentKitName;
         root.addProperty("current", currentName);
      }

      Iterator<Kit> kits = this.getRegistry().iterator();
      while (kits.hasNext()) {
         Kit kit = kits.next();
         JsonArray items = new JsonArray();
         kit.getItemsBySlot().forEach((BiConsumer<Integer, Kit.ItemDescriptor>)(slot, descriptor) -> {
            JsonObject itemObject = new JsonObject();
            int itemId = descriptor.itemId();
            Item item = Item.byId(itemId);
            Optional itemResourceKey = BuiltInRegistries.ITEM.getResourceKey(item);
            itemResourceKey.ifPresent((Consumer<ResourceKey>)resourceKey -> {
               Identifier identifier = resourceKey.identifier();
               String identifierText = identifier.toString();
               itemObject.addProperty("id", identifierText);
            });
            if (descriptor.potionSignature() != null) {
               String potionSignature = descriptor.potionSignature();
               itemObject.addProperty("data", potionSignature);
            }

            JsonObject slotObject = new JsonObject();
            slotObject.addProperty("slot", slot);
            slotObject.add("item", itemObject);
            items.add(slotObject);
         });
         String kitName = kit.getName();
         root.add(kitName, items);
      }
      return root;
   }

   @Override
   public String getConfigName() {
      return "kits.json";
   }

   private static int capturedItemId(ItemStack stack) {
      Item item = stack.getItem();
      return Item.getId(item);
   }

   public void captureInventory(String name) {
      this.getRegistry().removeIf((Predicate<Kit>)existingKit -> existingKit.getName().equalsIgnoreCase(name));
      Kit kit = new Kit(name);
      int slot = 0;
      LocalPlayer player = ClientAccess.collectiveMinecraft.player;
      Inventory inventory = player.getInventory();
      NonNullList inventoryItems = inventory.getNonEquipmentItems();

      for (Iterator stacks = inventoryItems.iterator(); stacks.hasNext(); slot++) {
         ItemStack stack = (ItemStack)stacks.next();

         Map itemsBySlot = kit.getItemsBySlot();
         Integer boxedSlot = slot;
         itemsBySlot.put(
            boxedSlot,
            new Kit.ItemDescriptor(capturedItemId(stack), getPotionSignature(stack))
         );
      }

      this.getRegistry().add(kit);
      this.setCurrentKitName(name);
   }

}
