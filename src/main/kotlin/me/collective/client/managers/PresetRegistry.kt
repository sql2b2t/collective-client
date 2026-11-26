package me.collective.client.managers

import com.google.gson.JsonParser
import me.collective.client.ClientAccess
import me.collective.client.config.PresetData
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.FileUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Predicate

class PresetRegistry(private val category: PresetCategory) : Registry<PresetData>(), ClientAccess {
    private val directory: Path = category.directory

    fun applyPreset(name: String): Boolean {
        synchronized(getRegistry()) {
            for (preset in getRegistry()) {
                if (preset.getName().equals(name, ignoreCase = true)) {
                    category.fromJson(preset.contents)
                    return true
                }
            }
            return false
        }
    }

    fun getPreset(name: String): PresetData? {
        synchronized(getRegistry()) {
            for (preset in entries) {
                if (preset.getName().equals(name, ignoreCase = true)) {
                    return preset
                }
            }
            return null
        }
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun restorePreset(preset: PresetData, replacedName: String = preset.getName()) {
        writePresetForUndo(preset)
        val replacedPath = resolvePresetFile(replacedName)
        val restoredPath = resolvePresetFile(preset.getName())
        if (replacedName != preset.getName() && Files.exists(replacedPath)
            && replacedPath.toAbsolutePath().normalize() != restoredPath.toAbsolutePath().normalize()
        ) {
            Files.delete(replacedPath)
        }
        entries.removeIf(Predicate { candidate -> candidate.getName().equals(preset.getName(), ignoreCase = true) })
        entries.add(preset)
    }

    /**
     * Резолвит файл пресета внутри каталога пресетов.
     * Имена приходят из пользовательского ввода, поэтому отсекаются пути
     * с разделителями и `..` (path traversal).
     */
    private fun resolvePresetFile(name: String?): Path {
        if (name == null) {
            throw IllegalArgumentException("Preset name is null")
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw IllegalArgumentException("Invalid preset name: $name")
        }
        val resolved = directory.resolve("$trimmed.json").normalize()
        if (!resolved.startsWith(directory.normalize())) {
            throw IllegalArgumentException("Preset path escapes preset directory: $name")
        }
        return resolved
    }

    @Throws(IOException::class)
    private fun writePresetForUndo(preset: PresetData) {
        val temporary = Files.createTempFile(directory, ".collective-preset-", ".tmp")
        try {
            FileUtils.writeUtf8(temporary, ClientAccess.gson.toJson(preset.contents))
            try {
                Files.move(
                    temporary,
                    resolvePresetFile(preset.getName()),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: IOException) {
                // На Windows атомарный rename поверх существующего файла может упасть,
                // поэтому повторяем обычным move с заменой.
                Files.move(temporary, resolvePresetFile(preset.getName()), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Throws(IOException::class)
    fun deletePresetForUndo(name: String) {
        val path = resolvePresetFile(name)
        if (Files.isDirectory(path)) {
            throw IOException("Preset path is a directory: ${path.fileName}")
        }
        Files.deleteIfExists(path)
        entries.removeIf(Predicate { candidate -> candidate.getName().equals(name, ignoreCase = true) })
    }

    @Throws(IOException::class)
    fun renamePresetForUndo(
        sourceName: String,
        targetName: String,
        previousTarget: PresetData?,
        source: PresetData?
    ) {
        if (source == null) {
            throw IOException("Preset no longer exists: $sourceName")
        }
        val sourcePath = resolvePresetFile(sourceName)
        val targetPath = resolvePresetFile(targetName)
        if (!Files.isRegularFile(sourcePath)) {
            throw IOException("Preset file no longer exists: ${sourcePath.fileName}")
        }
        Files.move(sourcePath, targetPath)
        try {
            if (previousTarget != null) {
                writePresetForUndo(previousTarget)
            }
        } catch (failure: IOException) {
            try {
                Files.move(targetPath, sourcePath)
            } catch (rollbackFailure: IOException) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
        entries.removeIf(Predicate { candidate -> candidate.getName().equals(sourceName, ignoreCase = true) })
        entries.add(PresetData(targetName, source.contents.deepCopy()))
        if (previousTarget != null) {
            entries.add(previousTarget)
        }
    }

    @Throws(IOException::class)
    fun savePreset(name: String) {
        resolvePresetFile(name) // валидация имени против path traversal
        entries.removeIf(Predicate { candidate -> candidate.getName().equals(name, ignoreCase = true) })
        val preset = PresetData(name, category.toJson().asJsonObject)
        preset.saveToDirectory(directory)
        entries.add(preset)
    }

    fun deletePreset(name: String): Boolean {
        val file = try {
            resolvePresetFile(name).toFile()
        } catch (_: IllegalArgumentException) {
            return false
        }
        file.delete()
        return entries.removeIf(Predicate { candidate -> candidate.getName().equals(name, ignoreCase = true) })
    }

    fun renamePreset(oldName: String, newName: String): Boolean {
        if (oldName.equals(newName, ignoreCase = true)) {
            return false
        }

        val sourceFile = try {
            resolvePresetFile(oldName).toFile()
        } catch (_: IllegalArgumentException) {
            return false
        }
        val targetFile = try {
            resolvePresetFile(newName).toFile()
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (sourceFile.exists()) {
            sourceFile.renameTo(targetFile)
        }

        return copyPreset(oldName, newName) &&
            entries.removeIf(Predicate { candidate -> candidate.getName().equals(oldName, ignoreCase = true) })
    }

    fun loadPresets() {
        synchronized(getRegistry()) {
            entries.clear()
            try {
                val files = Objects.requireNonNull(directory.toFile().listFiles())
                for (file in files) {
                    val fileName = file.name
                    if (fileName.endsWith(".json") && file.length() > 0L) {
                        try {
                            val jsonText = FileUtils.readUtf8(file.toPath())
                            val contents = JsonParser.parseString(jsonText).asJsonObject
                            if (contents != null) {
                                entries.add(PresetData(file.name.replace(".json", ""), contents))
                            }
                        } catch (entryFailure: Throwable) {
                            entryFailure.printStackTrace()
                        }
                    }
                }
            } catch (directoryFailure: Exception) {
                directoryFailure.printStackTrace()
            }
        }
    }

    fun copyPreset(sourceName: String, targetName: String): Boolean {
        for (preset in entries) {
            if (preset.getName().equals(sourceName, ignoreCase = true)) {
                return try {
                    val serializedContents = ClientAccess.gson.toJson(preset.contents)
                    FileUtils.writeUtf8(resolvePresetFile(targetName), serializedContents)
                    entries.add(PresetData(targetName, preset.contents.deepCopy()))
                    true
                } catch (writeFailure: IOException) {
                    writeFailure.printStackTrace()
                    false
                }
            }
        }
        return false
    }

    @Throws(IOException::class)
    fun savePresetByExactName(name: String) {
        for (preset in entries) {
            if (preset.getName() == name) {
                preset.saveToDirectory(directory)
            }
        }
    }

    fun forEachPreset(consumer: Consumer<PresetData>) {
        synchronized(getRegistry()) {
            getRegistry().forEach(consumer)
        }
    }

    fun saveAllPresets() {
        for (preset in entries) {
            try {
                preset.saveToDirectory(directory)
            } catch (writeFailure: IOException) {
                writeFailure.printStackTrace()
            }
        }
    }
}
