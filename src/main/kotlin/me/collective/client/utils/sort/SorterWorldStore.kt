package me.collective.client.utils.sort

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Хранит зоны и метки сортировщика, отдельно на каждый сервер.
 *
 * <p>Запись идёт во временный файл и только потом подменяет основной,
 * а предыдущая версия остаётся в backup — оборванная запись не должна
 * съесть настройки зон.
 */
class SorterWorldStore {
    private val zones = ArrayList<SortZone>()
    private val pins = ArrayList<ChestPin>()
    private var dirty = false
    private var dataFile: File? = null
    private var backupFile: File? = null
    private var tempFile: File? = null

    init {
        initializeFiles()
    }

    private fun initializeFiles() {
        try {
            val baseFolder: File = FabricLoader.getInstance().gameDir.resolve("collective").resolve("StashSorter").toFile()
            if (!baseFolder.exists() && !baseFolder.mkdirs()) LOGGER.error("Не удалось создать папку StashSorter")

            val folder = File(baseFolder, ContainerGeometry.serverId())
            if (!folder.exists() && !folder.mkdirs()) {
                LOGGER.error("Не удалось создать папку StashSorter для сервера")
            }

            dataFile = File(folder, "sorter_data.json")
            backupFile = File(folder, "sorter_data.backup.json")
            tempFile = File(folder, "sorter_data.tmp")
        } catch (e: Exception) {
            LOGGER.error("Не удалось подготовить файлы сортировщика", e)
        }
    }

    fun reinitializeForNewServer() {
        initializeFiles()
    }

    fun isDirty(): Boolean {
        return dirty
    }

    fun allZones(): List<SortZone> {
        return ArrayList(zones)
    }

    fun sourceZones(dim: String): List<SortZone> {
        val out = ArrayList<SortZone>()
        for (z in zones) {
            if (z.role === SortZone.ZoneRole.SOURCE && z.dimension == dim) out.add(z)
        }
        return out
    }

    fun destinationZones(dim: String): List<SortZone> {
        val out = ArrayList<SortZone>()
        for (z in zones) {
            if (z.role === SortZone.ZoneRole.DESTINATION && z.dimension == dim) out.add(z)
        }
        return out
    }

    fun addZone(zone: SortZone) {
        zones.add(zone)
        dirty = true
        save()
    }

    fun removeZone(zone: SortZone) {
        if (zones.remove(zone)) {
            dirty = true
            save()
        }
    }

    fun clearSourceZones(dim: String): Int {
        val before = zones.size
        zones.removeIf { z -> z.role === SortZone.ZoneRole.SOURCE && z.dimension == dim }
        val removed = before - zones.size
        if (removed > 0) {
            dirty = true
            save()
        }
        return removed
    }

    fun pinFor(pos: BlockPos, dim: String): String? {
        for (p in pins) if (p.matches(pos, dim)) return p.groupKey
        return null
    }

    fun setPin(pos: BlockPos, dim: String, groupKey: String) {
        pins.removeIf { p -> p.matches(pos, dim) }
        pins.add(ChestPin(pos, dim, groupKey))
        dirty = true
        save()
    }

    fun clearPin(pos: BlockPos, dim: String): Boolean {
        val removed = pins.removeIf { p -> p.matches(pos, dim) }
        if (removed) {
            dirty = true
            save()
        }
        return removed
    }

    fun pinsFor(dim: String): List<ChestPin> {
        val out = ArrayList<ChestPin>()
        for (p in pins) if (p.dimension == dim) out.add(p)
        return out
    }

    fun save() {
        val file = dataFile ?: return

        try {
            val root = JsonObject()
            root.addProperty("version", 1)

            val zonesJson = JsonArray()
            for (z in zones) zonesJson.add(GSON.toJsonTree(z))
            root.add("zones", zonesJson)

            val pinsJson = JsonArray()
            for (p in pins) pinsJson.add(GSON.toJsonTree(p))
            root.add("pins", pinsJson)

            OutputStreamWriter(FileOutputStream(tempFile!!), StandardCharsets.UTF_8).use { writer ->
                GSON.toJson(root, writer)
            }

            if (file.exists() && file.length() > 0L) {
                Files.copy(file.toPath(), backupFile!!.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(tempFile!!.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dirty = false
        } catch (e: Exception) {
            LOGGER.error("Не удалось сохранить данные сортировщика", e)
        }
    }

    fun load() {
        zones.clear()
        pins.clear()
        dirty = false
        if (loadFromFile(dataFile)) return
        if (loadFromFile(backupFile)) {
            LOGGER.warn("Основной файл сортировщика повреждён, данные взяты из резервной копии")
            save()
        }
    }

    private fun loadFromFile(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() == 0L) return false

        try {
            val root: JsonObject = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).asJsonObject

            if (root.has("zones")) {
                for (el in root.getAsJsonArray("zones")) {
                    val z = GSON.fromJson(el, SortZone::class.java)
                    if (z != null) zones.add(z)
                }
            }
            if (root.has("pins")) {
                for (el in root.getAsJsonArray("pins")) {
                    val p = GSON.fromJson(el, ChestPin::class.java)
                    if (p != null) pins.add(p)
                }
            }
            return true
        } catch (e: Exception) {
            LOGGER.error("Не удалось прочитать данные сортировщика из {}", file.getName(), e)
            zones.clear()
            pins.clear()
            return false
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Collective/StashSorter")
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }
}
