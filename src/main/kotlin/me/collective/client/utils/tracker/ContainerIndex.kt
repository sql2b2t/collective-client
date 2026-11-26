package me.collective.client.utils.tracker

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.collective.client.utils.sort.ContainerGeometry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Индекс содержимого контейнеров: что и где лежит, по измерениям.
 *
 * <p>Данные пишутся отдельно на каждый сервер. Запись идёт во временный файл
 * и только потом подменяет основной — оборванная запись не должна убить индекс.
 */
class ContainerIndex {
    private val containers: MutableMap<String, MutableMap<BlockPos, TrackedContainer>> = ConcurrentHashMap()
    private val lock = ReentrantReadWriteLock()

    private var dataFile: File? = null
    private var backupFile: File? = null
    private var tempFile: File? = null
    private var saveFailures = 0
    @Volatile
    private var dirty = false

    init {
        initializeFiles()
    }

    private fun initializeFiles() {
        try {
            val serverIdentifier = getServerIdentifier()
            val baseFolder = FabricLoader.getInstance().gameDir.resolve("collective").resolve("ChestTracker").toFile()
            if (!baseFolder.exists() && !baseFolder.mkdirs()) LOGGER.error("Не удалось создать папку ChestTracker")

            val folder = File(baseFolder, serverIdentifier)
            if (!folder.exists() && !folder.mkdirs()) {
                LOGGER.error("Не удалось создать папку ChestTracker для сервера: {}", serverIdentifier)
            }

            dataFile = File(folder, "tracked_containers.json")
            backupFile = File(folder, "tracked_containers.backup.json")
            tempFile = File(folder, "tracked_containers.tmp")
        } catch (e: Exception) {
            LOGGER.error("Не удалось подготовить файлы индекса", e)
        }
    }

    private fun getServerIdentifier(): String {
        val mc = Minecraft.getInstance()
        if (mc == null) return "unknown"
        val currentServer = mc.currentServer
        if (currentServer != null) return ContainerGeometry.sanitizeFileName(currentServer.ip)
        if (mc.isLocalServer && mc.singleplayerServer != null) {
            val singleplayerServer = mc.singleplayerServer ?: return "unknown"
            return "singleplayer_" + ContainerGeometry.sanitizeFileName(singleplayerServer.worldData.levelName)
        }
        return "unknown"
    }

    /** Смена сервера в рамках одной сессии — индекс другой. */
    fun reinitializeForNewServer() {
        initializeFiles()
    }

    fun trackContainer(pos: BlockPos, dimension: String, containerType: String, contents: List<ItemStack?>) {
        lock.writeLock().lock()
        try {
            val dimContainers = containers.computeIfAbsent(dimension) { ConcurrentHashMap() }
            val container = dimContainers.computeIfAbsent(pos) { p -> TrackedContainer(p, dimension, containerType) }
            container.updateContents(contents)
            dirty = true
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun removeContainer(pos: BlockPos, dimension: String) {
        lock.writeLock().lock()
        try {
            val dimContainers = containers[dimension]
            if (dimContainers != null && dimContainers.remove(pos) != null) dirty = true
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun isDirty(): Boolean {
        return dirty
    }

    fun getContainer(pos: BlockPos, dimension: String): TrackedContainer? {
        lock.readLock().lock()
        try {
            val dimContainers = containers[dimension]
            return dimContainers?.get(pos)
        } finally {
            lock.readLock().unlock()
        }
    }

    /** Контейнеры, где есть предмет — от большего количества к меньшему. */
    fun searchItem(item: Item): List<TrackedContainer> {
        return searchItem(item, currentDimension())
    }

    fun searchItem(item: Item, dimension: String): List<TrackedContainer> {
        val itemId = BuiltInRegistries.ITEM.getKey(item).toString()
        lock.readLock().lock()
        try {
            val dimContainers = containers[dimension] ?: return ArrayList()
            val found = ArrayList<TrackedContainer>()
            for (c in dimContainers.values) if (c.containsItem(item)) found.add(c)
            found.sortByDescending { it.getItemCount(itemId) }
            return found
        } finally {
            lock.readLock().unlock()
        }
    }

    fun searchSignature(sig: String): List<TrackedContainer> {
        return searchSignature(sig, currentDimension())
    }

    fun searchSignature(sig: String, dimension: String): List<TrackedContainer> {
        lock.readLock().lock()
        try {
            val dimContainers = containers[dimension] ?: return ArrayList()
            val found = ArrayList<TrackedContainer>()
            for (c in dimContainers.values) if (c.containsSignature(sig)) found.add(c)
            found.sortByDescending { it.getSignatureCount(sig) }
            return found
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getAllContainers(): List<TrackedContainer> {
        return getAllContainers(currentDimension())
    }

    fun getAllContainers(dimension: String): List<TrackedContainer> {
        lock.readLock().lock()
        try {
            val dimContainers = containers[dimension]
            return if (dimContainers != null) ArrayList(dimContainers.values) else ArrayList()
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getTotalContainerCount(): Int {
        lock.readLock().lock()
        try {
            return containers.values.sumOf { it.size }
        } finally {
            lock.readLock().unlock()
        }
    }

    fun clearAll() {
        lock.writeLock().lock()
        try {
            containers.clear()
            dirty = true
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun clearCurrentDimension() {
        lock.writeLock().lock()
        try {
            containers.remove(currentDimension())
            dirty = true
        } finally {
            lock.writeLock().unlock()
        }
    }

    /** Запись: сначала во временный файл, затем копия старого в backup и подмена. */
    fun saveData() {
        lock.readLock().lock()
        try {
            val root = JsonObject()
            root.addProperty("version", 2)
            root.addProperty("saveTime", System.currentTimeMillis())
            root.add("dimensions", dimensionsToJson())

            OutputStreamWriter(FileOutputStream(tempFile!!), StandardCharsets.UTF_8).use { writer ->
                GSON.toJson(root, writer)
            }

            if (dataFile!!.exists() && dataFile!!.length() > 0L) {
                Files.copy(dataFile!!.toPath(), backupFile!!.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(tempFile!!.toPath(), dataFile!!.toPath(), StandardCopyOption.REPLACE_EXISTING)

            saveFailures = 0
            dirty = false
        } catch (e: Exception) {
            saveFailures++
            LOGGER.error("Не удалось сохранить индекс (попытка {})", saveFailures, e)
            if (saveFailures > 3) LOGGER.error("Многократные ошибки сохранения — индекс может быть потерян")
        } finally {
            lock.readLock().unlock()
        }
    }

    fun loadData() {
        lock.writeLock().lock()
        try {
            containers.clear()
            dirty = false

            if (loadFromFile(dataFile)) return
            // Основной файл битый — пробуем резервную копию
            if (loadFromFile(backupFile)) {
                LOGGER.warn("Основной файл повреждён, данные взяты из резервной копии")
                saveData()
                return
            }

            val oldFile = FabricLoader.getInstance().gameDir.resolve("collective/ChestTracker/tracked_containers.json").toFile()
            if (!oldFile.exists() || !loadFromFile(oldFile)) return

            LOGGER.info("Данные перенесены из старого формата")
            saveData()
        } finally {
            lock.writeLock().unlock()
        }
    }

    private fun loadFromFile(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() == 0L) return false

        try {
            val root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).asJsonObject
            if (!root.has("dimensions")) return false

            val dimensions = root.getAsJsonObject("dimensions")
            for (dimKey in dimensions.keySet()) {
                val dimContainers: MutableMap<BlockPos, TrackedContainer> = ConcurrentHashMap()
                for (element in dimensions.getAsJsonArray(dimKey)) {
                    // Одна битая запись не должна ронять весь индекс
                    try {
                        val container = TrackedContainer.fromJson(element.asJsonObject)
                        dimContainers[container.position] = container
                    } catch (e: Exception) {
                        LOGGER.warn("Пропущена повреждённая запись контейнера", e)
                    }
                }
                if (dimContainers.isNotEmpty()) containers[dimKey] = dimContainers
            }
            return true
        } catch (e: Exception) {
            LOGGER.error("Не удалось прочитать файл индекса: {}", file.name, e)
            return false
        }
    }

    @Throws(java.io.IOException::class)
    fun exportData(filename: String) {
        lock.readLock().lock()
        try {
            val exportFile = FabricLoader.getInstance().gameDir.resolve("collective/ChestTracker").resolve(filename).toFile()
            val export = JsonObject()
            export.addProperty("version", 2)
            export.addProperty("exportTime", System.currentTimeMillis())
            export.addProperty("totalContainers", getTotalContainerCount())
            export.add("dimensions", dimensionsToJson())

            OutputStreamWriter(FileOutputStream(exportFile), StandardCharsets.UTF_8).use { writer ->
                GSON.toJson(export, writer)
            }
        } finally {
            lock.readLock().unlock()
        }
    }

    private fun dimensionsToJson(): JsonObject {
        val dimensions = JsonObject()
        for ((dimKey, dimMap) in containers) {
            val dimArray = JsonArray()
            for (container in dimMap.values) dimArray.add(container.toJson())
            dimensions.add(dimKey, dimArray)
        }
        return dimensions
    }

    private fun currentDimension(): String {
        val mc = Minecraft.getInstance()
        val level = mc?.level ?: return "unknown"
        return level.dimension().identifier().toString()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Collective/ChestTracker")
        private val GSON = GsonBuilder().setPrettyPrinting().create()
    }
}
