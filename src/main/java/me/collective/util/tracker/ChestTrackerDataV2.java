package me.collective.util.tracker;

import me.collective.util.sort.ContainerGeometry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Индекс содержимого контейнеров: что и где лежит, по измерениям.
 *
 * <p>Данные пишутся отдельно на каждый сервер. Запись идёт во временный файл
 * и только потом подменяет основной — оборванная запись не должна убить индекс.
 */
public class ChestTrackerDataV2 {
    private static final Logger LOGGER = LoggerFactory.getLogger("B2XY/ChestTracker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Map<BlockPos, TrackedContainer>> containers = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Minecraft mc = Minecraft.getInstance();

    private File dataFile;
    private File backupFile;
    private File tempFile;
    private int saveFailures = 0;
    private volatile boolean dirty = false;

    public ChestTrackerDataV2() {
        initializeFiles();
    }

    private void initializeFiles() {
        try {
            String serverIdentifier = getServerIdentifier();
            File baseFolder = new File(new File(net.minecraft.client.Minecraft.getInstance().runDirectory, "collective"), "ChestTracker");
            if (!baseFolder.exists() && !baseFolder.mkdirs()) LOGGER.error("Не удалось создать папку ChestTracker");

            File folder = new File(baseFolder, serverIdentifier);
            if (!folder.exists() && !folder.mkdirs()) {
                LOGGER.error("Не удалось создать папку ChestTracker для сервера: {}", serverIdentifier);
            }

            dataFile = new File(folder, "tracked_containers.json");
            backupFile = new File(folder, "tracked_containers.backup.json");
            tempFile = new File(folder, "tracked_containers.tmp");
        } catch (Exception e) {
            LOGGER.error("Не удалось подготовить файлы индекса", e);
        }
    }

    private String getServerIdentifier() {
        if (mc == null) return "unknown";
        if (mc.getCurrentServerEntry() != null) return ContainerGeometry.sanitizeFileName(mc.getCurrentServerEntry().address);
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return "singleplayer_" + ContainerGeometry.sanitizeFileName(mc.getServer().getSaveProperties().getLevelName());
        }
        return "unknown";
    }

    /** Смена сервера в рамках одной сессии — индекс другой. */
    public void reinitializeForNewServer() {
        initializeFiles();
    }

    public void trackContainer(BlockPos pos, String dimension, String containerType, List<ItemStack> contents) {
        lock.writeLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
            TrackedContainer container = dimContainers.computeIfAbsent(pos,
                p -> new TrackedContainer(p, dimension, containerType));
            container.updateContents(contents);
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeContainer(BlockPos pos, String dimension) {
        lock.writeLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            if (dimContainers != null && dimContainers.remove(pos) != null) dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public TrackedContainer getContainer(BlockPos pos, String dimension) {
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            return dimContainers != null ? dimContainers.get(pos) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Контейнеры, где есть предмет — от большего количества к меньшему. */
    public List<TrackedContainer> searchItem(Item item) {
        return searchItem(item, currentDimension());
    }

    public List<TrackedContainer> searchItem(Item item, String dimension) {
        String itemId = Registries.ITEM.getId(item).toString();
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            if (dimContainers == null) return new ArrayList<>();
            List<TrackedContainer> found = new ArrayList<>();
            for (TrackedContainer c : dimContainers.values()) if (c.containsItem(item)) found.add(c);
            found.sort(Comparator.comparingInt((TrackedContainer c) -> c.getItemCount(itemId)).reversed());
            return found;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<TrackedContainer> searchSignature(String sig) {
        return searchSignature(sig, currentDimension());
    }

    public List<TrackedContainer> searchSignature(String sig, String dimension) {
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            if (dimContainers == null) return new ArrayList<>();
            List<TrackedContainer> found = new ArrayList<>();
            for (TrackedContainer c : dimContainers.values()) if (c.containsSignature(sig)) found.add(c);
            found.sort(Comparator.comparingInt((TrackedContainer c) -> c.getSignatureCount(sig)).reversed());
            return found;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<TrackedContainer> getAllContainers() {
        return getAllContainers(currentDimension());
    }

    public List<TrackedContainer> getAllContainers(String dimension) {
        lock.readLock().lock();
        try {
            Map<BlockPos, TrackedContainer> dimContainers = containers.get(dimension);
            return dimContainers != null ? new ArrayList<>(dimContainers.values()) : new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTotalContainerCount() {
        lock.readLock().lock();
        try {
            return containers.values().stream().mapToInt(Map::size).sum();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearAll() {
        lock.writeLock().lock();
        try {
            containers.clear();
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearCurrentDimension() {
        lock.writeLock().lock();
        try {
            containers.remove(currentDimension());
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Запись: сначала во временный файл, затем копия старого в backup и подмена. */
    public void saveData() {
        lock.readLock().lock();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            root.addProperty("saveTime", System.currentTimeMillis());
            root.add("dimensions", dimensionsToJson());

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            if (dataFile.exists() && dataFile.length() > 0L) {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            saveFailures = 0;
            dirty = false;
        } catch (Exception e) {
            saveFailures++;
            LOGGER.error("Не удалось сохранить индекс (попытка {})", saveFailures, e);
            if (saveFailures > 3) LOGGER.error("Многократные ошибки сохранения — индекс может быть потерян");
        } finally {
            lock.readLock().unlock();
        }
    }

    public void loadData() {
        lock.writeLock().lock();
        try {
            containers.clear();
            dirty = false;

            if (loadFromFile(dataFile)) return;
            // Основной файл битый — пробуем резервную копию
            if (loadFromFile(backupFile)) {
                LOGGER.warn("Основной файл повреждён, данные взяты из резервной копии");
                saveData();
                return;
            }

            File oldFile = new File(new File(net.minecraft.client.Minecraft.getInstance().runDirectory, "collective"), "ChestTracker/tracked_containers.json");
            if (!oldFile.exists() || !loadFromFile(oldFile)) return;

            LOGGER.info("Данные перенесены из старого формата");
            saveData();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean loadFromFile(File file) {
        if (!file.exists() || file.length() == 0L) return false;

        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("dimensions")) return false;

            JsonObject dimensions = root.getAsJsonObject("dimensions");
            for (Entry<String, JsonElement> dimEntry : dimensions.entrySet()) {
                Map<BlockPos, TrackedContainer> dimContainers = new ConcurrentHashMap<>();
                for (JsonElement element : dimEntry.getValue().getAsJsonArray()) {
                    // Одна битая запись не должна ронять весь индекс
                    try {
                        TrackedContainer container = TrackedContainer.fromJson(element.getAsJsonObject());
                        dimContainers.put(container.getPosition(), container);
                    } catch (Exception e) {
                        LOGGER.warn("Пропущена повреждённая запись контейнера", e);
                    }
                }
                if (!dimContainers.isEmpty()) containers.put(dimEntry.getKey(), dimContainers);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Не удалось прочитать файл индекса: {}", file.getName(), e);
            return false;
        }
    }

    public void exportData(String filename) throws java.io.IOException {
        lock.readLock().lock();
        try {
            File exportFile = new File(new File(new File(net.minecraft.client.Minecraft.getInstance().runDirectory, "collective"), "ChestTracker"), filename);
            JsonObject export = new JsonObject();
            export.addProperty("version", 2);
            export.addProperty("exportTime", System.currentTimeMillis());
            export.addProperty("totalContainers", getTotalContainerCount());
            export.add("dimensions", dimensionsToJson());

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
                GSON.toJson(export, writer);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private JsonObject dimensionsToJson() {
        JsonObject dimensions = new JsonObject();
        for (Entry<String, Map<BlockPos, TrackedContainer>> dimEntry : containers.entrySet()) {
            JsonArray dimArray = new JsonArray();
            for (TrackedContainer container : dimEntry.getValue().values()) dimArray.add(container.toJson());
            dimensions.add(dimEntry.getKey(), dimArray);
        }
        return dimensions;
    }

    private String currentDimension() {
        if (mc == null || mc.world == null) return "unknown";
        RegistryKey<Level> key = mc.world.getRegistryKey();
        return key.getValue().toString();
    }
}