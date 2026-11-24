package me.collective.client.utils.tracker

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.sort.ItemSignature
import me.collective.client.utils.sort.ShulkerDataParser
import me.collective.client.utils.sort.SortGroupKey
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import java.util.ArrayList
import java.util.HashMap

/**
 * Снимок содержимого одного контейнера, каким он был при последнем открытии.
 *
 * <p>Хранится три способами сразу, потому что задачи разные:
 * предметы нужны для поиска, подписи — чтобы не смешивать зачарованное,
 * а ключи групп ([SortGroupKey]) — для решения сортировки.
 */
class TrackedContainer(
    val position: BlockPos,
    val dimension: String,
    var containerType: String
) {
    private var customName: String? = null
    private val items = HashMap<String, Int>()
    private val sigCounts = HashMap<String, Int>()
    private val topSigCounts = HashMap<String, Int>()
    private val itemStacks = ArrayList<ItemStack>()
    var lastUpdated: Long = System.currentTimeMillis()
        private set

    /** Полная перезапись: содержимое сундука могло измениться с прошлого раза. */
    fun updateContents(stacks: List<ItemStack?>) {
        items.clear()
        sigCounts.clear()
        topSigCounts.clear()
        this.itemStacks.clear()

        for (stack in stacks) {
            if (stack == null || stack.isEmpty) continue
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            items.merge(itemId, stack.count) { previous, current -> previous + current }
            sigCounts.merge(ItemSignature.of(stack), stack.count) { previous, current -> previous + current }
            topSigCounts.merge(SortGroupKey.of(stack), stack.count) { previous, current -> previous + current }
            this.itemStacks.add(stack.copy())
            indexNestedItems(stack)
        }

        lastUpdated = System.currentTimeMillis()
    }

    /** Предметы внутри шалкера тоже ищутся — иначе «сундук с обсидианом» не найдётся. */
    private fun indexNestedItems(stack: ItemStack) {
        if (!ContainerGeometry.isShulkerItem(stack)) return
        for (nested in ShulkerDataParser.parseShulkerContentsAsList(stack)) {
            if (nested == null || nested.isEmpty) continue
            val itemId = BuiltInRegistries.ITEM.getKey(nested.item).toString()
            items.merge(itemId, nested.count) { previous, current -> previous + current }
            sigCounts.merge(ItemSignature.of(nested), nested.count) { previous, current -> previous + current }
        }
    }

    fun containsItem(item: Item): Boolean {
        return items.containsKey(BuiltInRegistries.ITEM.getKey(item).toString())
    }

    fun containsSignature(sig: String): Boolean {
        return sigCounts.containsKey(sig)
    }

    fun getSignatureCount(sig: String): Int {
        return sigCounts.getOrDefault(sig, 0)
    }

    fun getItemCount(itemId: String): Int {
        return items.getOrDefault(itemId, 0)
    }

    fun getItems(): Map<String, Int> {
        return HashMap(items)
    }

    fun getTopLevelSignatureCounts(): Map<String, Int> {
        return HashMap(topSigCounts)
    }

    fun getItemStacks(): List<ItemStack> {
        return ArrayList(itemStacks)
    }

    /** Сколько стопок реально лежит в контейнере — грубая занятость. */
    val usedStacks: Int
        get() = itemStacks.size

    fun isEmpty(): Boolean {
        return items.isEmpty()
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("x", position.x)
        json.addProperty("y", position.y)
        json.addProperty("z", position.z)
        json.addProperty("dimension", dimension)
        json.addProperty("type", containerType)
        json.addProperty("lastUpdated", lastUpdated)
        customName?.let { json.addProperty("customName", it) }

        json.add("items", intMapToJson(items))
        json.add("sigs", intMapToJson(sigCounts))
        json.add("topSigs", intMapToJson(topSigCounts))

        val stacksJson = JsonArray()
        for (stack in itemStacks) {
            val s = JsonObject()
            s.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.item).toString())
            s.addProperty("count", stack.count)

            if (ContainerGeometry.isShulkerItem(stack)) {
                val nestedJson = JsonArray()
                for (nested in ShulkerDataParser.parseShulkerContentsAsList(stack)) {
                    if (nested == null || nested.isEmpty) continue
                    val n = JsonObject()
                    n.addProperty("id", BuiltInRegistries.ITEM.getKey(nested.item).toString())
                    n.addProperty("count", nested.count)
                    nestedJson.add(n)
                }
                if (!nestedJson.isEmpty) s.add("nested", nestedJson)
            }

            stacksJson.add(s)
        }
        json.add("stacks", stacksJson)
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TrackedContainer &&
         position == other.position &&
         dimension == other.dimension
    }

    override fun hashCode(): Int {
        return position.hashCode() * 31 + dimension.hashCode()
    }

    companion object {
        private const val SHULKER_SLOTS = 27

        @JvmStatic
        fun fromJson(json: JsonObject): TrackedContainer {
            val pos = BlockPos(json.get("x").asInt, json.get("y").asInt, json.get("z").asInt)
            val dimension = json.get("dimension").asString
            val type = if (json.has("type")) json.get("type").asString else "chest"
            val container = TrackedContainer(pos, dimension, type)

            if (json.has("customName")) container.customName = json.get("customName").asString
            if (json.has("lastUpdated")) container.lastUpdated = json.get("lastUpdated").asLong
            if (json.has("items")) jsonToIntMap(json.getAsJsonObject("items"), container.items)
            if (json.has("sigs")) jsonToIntMap(json.getAsJsonObject("sigs"), container.sigCounts)

            if (json.has("stacks")) {
                for (el in json.getAsJsonArray("stacks")) {
                    val s = el.asJsonObject
                    val id = Identifier.tryParse(s.get("id").asString)
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue

                    val count = s.get("count").asInt
                    if (count <= 0) continue

                    val stack = ItemStack(BuiltInRegistries.ITEM.getValue(id), count)
                    if (s.has("nested")) {
                        val nested = ArrayList<ItemStack>()
                        for (ne in s.getAsJsonArray("nested")) {
                            if (nested.size >= SHULKER_SLOTS) break
                            val n = ne.asJsonObject
                            val nid = Identifier.tryParse(n.get("id").asString)
                            if (nid == null || !BuiltInRegistries.ITEM.containsKey(nid)) continue
                            val ncount = n.get("count").asInt
                            if (ncount > 0) nested.add(ItemStack(BuiltInRegistries.ITEM.getValue(nid), ncount))
                        }
                        if (!nested.isEmpty()) stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nested))
                    }

                    container.itemStacks.add(stack)
                }
            }

            if (json.has("topSigs")) {
                jsonToIntMap(json.getAsJsonObject("topSigs"), container.topSigCounts)
            } else {
                // Старый формат без topSigs — восстанавливаем из стопок
                for (stack in container.itemStacks) {
                    container.topSigCounts.merge(ItemSignature.of(stack), stack.count) { previous, current -> previous + current }
                }
            }

            return container
        }

        private fun intMapToJson(map: Map<String, Int>): JsonObject {
            val json = JsonObject()
            for ((key, value) in map) json.addProperty(key, value)
            return json
        }

        private fun jsonToIntMap(json: JsonObject, target: MutableMap<String, Int>) {
            for (key in json.keySet()) target[key] = json.get(key).asInt
        }
    }
}
