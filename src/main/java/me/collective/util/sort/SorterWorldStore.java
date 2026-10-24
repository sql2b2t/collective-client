package me.collective.util.sort;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
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
import java.util.List;

/**
 * Хранит зоны и метки сортировщика, отдельно на каждый сервер.
 *
 * <p>Запись идёт во временный файл и только потом подменяет основной,
 * а предыдущая версия остаётся в backup — оборванная запись не должна
 * съесть настройки зон.
 */
public class SorterWorldStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("B2XY/StashSorter");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<SortZone> zones = new ArrayList<>();
    private final List<ChestPin> pins = new ArrayList<>();
    private boolean dirty = false;
    private File dataFile;
    private File backupFile;
    private File tempFile;

    public SorterWorldStore() {
        initializeFiles();
    }

    private void initializeFiles() {
        try {
            File baseFolder = new File(new File(net.minecraft.client.Minecraft.getInstance().runDirectory, "collective"), "StashSorter");
            if (!baseFolder.exists() && !baseFolder.mkdirs()) LOGGER.error("Не удалось создать папку StashSorter");

            File folder = new File(baseFolder, ContainerGeometry.serverId());
            if (!folder.exists() && !folder.mkdirs()) {
                LOGGER.error("Не удалось создать папку StashSorter для сервера");
            }

            dataFile = new File(folder, "sorter_data.json");
            backupFile = new File(folder, "sorter_data.backup.json");
            tempFile = new File(folder, "sorter_data.tmp");
        } catch (Exception e) {
            LOGGER.error("Не удалось подготовить файлы сортировщика", e);
        }
    }

    public void reinitializeForNewServer() {
        initializeFiles();
    }

    public boolean isDirty() {
        return dirty;
    }

    public List<SortZone> allZones() {
        return new ArrayList<>(zones);
    }

    public List<SortZone> sourceZones(String dim) {
        List<SortZone> out = new ArrayList<>();
        for (SortZone z : zones) {
            if (z.role == SortZone.ZoneRole.SOURCE && z.dimension.equals(dim)) out.add(z);
        }
        return out;
    }

    public List<SortZone> destinationZones(String dim) {
        List<SortZone> out = new ArrayList<>();
        for (SortZone z : zones) {
            if (z.role == SortZone.ZoneRole.DESTINATION && z.dimension.equals(dim)) out.add(z);
        }
        return out;
    }

    public void addZone(SortZone zone) {
        zones.add(zone);
        dirty = true;
        save();
    }

    public void removeZone(SortZone zone) {
        if (zones.remove(zone)) {
            dirty = true;
            save();
        }
    }

    public int clearSourceZones(String dim) {
        int before = zones.size();
        zones.removeIf(z -> z.role == SortZone.ZoneRole.SOURCE && z.dimension.equals(dim));
        int removed = before - zones.size();
        if (removed > 0) {
            dirty = true;
            save();
        }
        return removed;
    }

    @Nullable
    public String pinFor(BlockPos pos, String dim) {
        for (ChestPin p : pins) if (p.matches(pos, dim)) return p.groupKey;
        return null;
    }

    public void setPin(BlockPos pos, String dim, String groupKey) {
        pins.removeIf(p -> p.matches(pos, dim));
        pins.add(new ChestPin(pos, dim, groupKey));
        dirty = true;
        save();
    }

    public boolean clearPin(BlockPos pos, String dim) {
        boolean removed = pins.removeIf(p -> p.matches(pos, dim));
        if (removed) {
            dirty = true;
            save();
        }
        return removed;
    }

    public List<ChestPin> pinsFor(String dim) {
        List<ChestPin> out = new ArrayList<>();
        for (ChestPin p : pins) if (p.dimension.equals(dim)) out.add(p);
        return out;
    }

    public void save() {
        if (dataFile == null) return;

        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);

            JsonArray zonesJson = new JsonArray();
            for (SortZone z : zones) zonesJson.add(GSON.toJsonTree(z));
            root.add("zones", zonesJson);

            JsonArray pinsJson = new JsonArray();
            for (ChestPin p : pins) pinsJson.add(GSON.toJsonTree(p));
            root.add("pins", pinsJson);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            if (dataFile.exists() && dataFile.length() > 0L) {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить данные сортировщика", e);
        }
    }

    public void load() {
        zones.clear();
        pins.clear();
        dirty = false;
        if (loadFromFile(dataFile)) return;
        if (loadFromFile(backupFile)) {
            LOGGER.warn("Основной файл сортировщика повреждён, данные взяты из резервной копии");
            save();
        }
    }

    private boolean loadFromFile(File file) {
        if (file == null || !file.exists() || file.length() == 0L) return false;

        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();

            if (root.has("zones")) {
                for (JsonElement el : root.getAsJsonArray("zones")) {
                    SortZone z = GSON.fromJson(el, SortZone.class);
                    if (z != null && z.dimension != null && z.role != null) zones.add(z);
                }
            }
            if (root.has("pins")) {
                for (JsonElement el : root.getAsJsonArray("pins")) {
                    ChestPin p = GSON.fromJson(el, ChestPin.class);
                    if (p != null && p.dimension != null && p.groupKey != null) pins.add(p);
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Не удалось прочитать данные сортировщика из {}", file.getName(), e);
            zones.clear();
            pins.clear();
            return false;
        }
    }
}