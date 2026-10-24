package me.collective.util.tracker;

import me.collective.util.sort.ContainerGeometry;
import me.collective.util.sort.ItemSignature;
import me.collective.util.sort.ShulkerDataParser;
import me.collective.util.sort.SortGroupKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Снимок содержимого одного контейнера, каким он был при последнем открытии.
 *
 * <p>Хранится три способами сразу, потому что задачи разные:
 * предметы нужны для поиска, подписи — чтобы не смешивать зачарованное,
 * а ключи групп ({@link SortGroupKey}) — для решения сортировки.
 */
public class TrackedContainer {
    private static final int SHULKER_SLOTS = 27;

    private final BlockPos position;
    private final String dimension;
    private String customName;
    private final Map<String, Integer> items = new HashMap<>();
    private final Map<String, Integer> sigCounts = new HashMap<>();
    private final Map<String, Integer> topSigCounts = new HashMap<>();
    private final List<ItemStack> itemStacks = new ArrayList<>();
    private long lastUpdated = System.currentTimeMillis();
    private String containerType;

    public TrackedContainer(BlockPos position, String dimension, String containerType) {
        this.position = position;
        this.dimension = dimension;
        this.containerType = containerType;
    }

    /** Полная перезапись: содержимое сундука могло измениться с прошлого раза. */
    public void updateContents(List<ItemStack> stacks) {
        items.clear();
        sigCounts.clear();
        topSigCounts.clear();
        itemStacks.clear();

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            items.merge(itemId, stack.getCount(), Integer::sum);
            sigCounts.merge(ItemSignature.of(stack), stack.getCount(), Integer::sum);
            topSigCounts.merge(SortGroupKey.of(stack), stack.getCount(), Integer::sum);
            itemStacks.add(stack.copy());
            indexNestedItems(stack);
        }

        lastUpdated = System.currentTimeMillis();
    }

    /** Предметы внутри шалкера тоже ищутся — иначе «сундук с обсидианом» не найдётся. */
    private void indexNestedItems(ItemStack stack) {
        if (!ContainerGeometry.isShulkerItem(stack)) return;
        for (ItemStack nested : ShulkerDataParser.parseShulkerContentsAsList(stack)) {
            if (nested == null || nested.isEmpty()) continue;
            String itemId = Registries.ITEM.getId(nested.getItem()).toString();
            items.merge(itemId, nested.getCount(), Integer::sum);
            sigCounts.merge(ItemSignature.of(nested), nested.getCount(), Integer::sum);
        }
    }

    public boolean containsItem(Item item) {
        return items.containsKey(Registries.ITEM.getId(item).toString());
    }

    public boolean containsSignature(String sig) {
        return sigCounts.containsKey(sig);
    }

    public int getSignatureCount(String sig) {
        return sigCounts.getOrDefault(sig, 0);
    }

    public int getItemCount(String itemId) {
        return items.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> getItems() {
        return new HashMap<>(items);
    }

    public Map<String, Integer> getTopLevelSignatureCounts() {
        return new HashMap<>(topSigCounts);
    }

    public List<ItemStack> getItemStacks() {
        return new ArrayList<>(itemStacks);
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    /** Сколько стопок реально лежит в контейнере — грубая занятость. */
    public int getUsedStacks() {
        return itemStacks.size();
    }

    public String getDimension() {
        return dimension;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("x", position.getX());
        json.addProperty("y", position.getY());
        json.addProperty("z", position.getZ());
        json.addProperty("dimension", dimension);
        json.addProperty("type", containerType);
        json.addProperty("lastUpdated", lastUpdated);
        if (customName != null) json.addProperty("customName", customName);

        json.add("items", intMapToJson(items));
        json.add("sigs", intMapToJson(sigCounts));
        json.add("topSigs", intMapToJson(topSigCounts));

        JsonArray stacksJson = new JsonArray();
        for (ItemStack stack : itemStacks) {
            JsonObject s = new JsonObject();
            s.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
            s.addProperty("count", stack.getCount());

            if (ContainerGeometry.isShulkerItem(stack)) {
                JsonArray nestedJson = new JsonArray();
                for (ItemStack nested : ShulkerDataParser.parseShulkerContentsAsList(stack)) {
                    if (nested == null || nested.isEmpty()) continue;
                    JsonObject n = new JsonObject();
                    n.addProperty("id", Registries.ITEM.getId(nested.getItem()).toString());
                    n.addProperty("count", nested.getCount());
                    nestedJson.add(n);
                }
                if (!nestedJson.isEmpty()) s.add("nested", nestedJson);
            }

            stacksJson.add(s);
        }
        json.add("stacks", stacksJson);
        return json;
    }

    public static TrackedContainer fromJson(JsonObject json) {
        BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
        String dimension = json.get("dimension").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "chest";
        TrackedContainer container = new TrackedContainer(pos, dimension, type);

        if (json.has("customName")) container.customName = json.get("customName").getAsString();
        if (json.has("lastUpdated")) container.lastUpdated = json.get("lastUpdated").getAsLong();
        if (json.has("items")) jsonToIntMap(json.getAsJsonObject("items"), container.items);
        if (json.has("sigs")) jsonToIntMap(json.getAsJsonObject("sigs"), container.sigCounts);

        if (json.has("stacks")) {
            for (JsonElement el : json.getAsJsonArray("stacks")) {
                JsonObject s = el.getAsJsonObject();
                Identifier id = Identifier.tryParse(s.get("id").getAsString());
                if (id == null || !Registries.ITEM.containsId(id)) continue;

                int count = s.get("count").getAsInt();
                if (count <= 0) continue;

                ItemStack stack = new ItemStack((ItemConvertible) Registries.ITEM.get(id), count);
                if (s.has("nested")) {
                    List<ItemStack> nested = new ArrayList<>();
                    for (JsonElement ne : s.getAsJsonArray("nested")) {
                        if (nested.size() >= SHULKER_SLOTS) break;
                        JsonObject n = ne.getAsJsonObject();
                        Identifier nid = Identifier.tryParse(n.get("id").getAsString());
                        if (nid == null || !Registries.ITEM.containsId(nid)) continue;
                        int ncount = n.get("count").getAsInt();
                        if (ncount > 0) nested.add(new ItemStack((ItemConvertible) Registries.ITEM.get(nid), ncount));
                    }
                    if (!nested.isEmpty()) stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(nested));
                }

                container.itemStacks.add(stack);
            }
        }

        if (json.has("topSigs")) {
            jsonToIntMap(json.getAsJsonObject("topSigs"), container.topSigCounts);
        } else {
            // Старый формат без topSigs — восстанавливаем из стопок
            for (ItemStack stack : container.itemStacks) {
                container.topSigCounts.merge(ItemSignature.of(stack), stack.getCount(), Integer::sum);
            }
        }

        return container;
    }

    private static JsonObject intMapToJson(Map<String, Integer> map) {
        JsonObject json = new JsonObject();
        for (Entry<String, Integer> entry : map.entrySet()) json.addProperty(entry.getKey(), entry.getValue());
        return json;
    }

    private static void jsonToIntMap(JsonObject json, Map<String, Integer> target) {
        for (String key : json.keySet()) target.put(key, json.get(key).getAsInt());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof TrackedContainer other
            && position.equals(other.position)
            && dimension.equals(other.dimension);
    }

    @Override
    public int hashCode() {
        return position.hashCode() * 31 + dimension.hashCode();
    }
}