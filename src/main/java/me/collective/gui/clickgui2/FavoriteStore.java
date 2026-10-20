package me.collective.gui.clickgui2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Избранные модули второго стиля: маленький json-файл в папке клиента
 * (collective/gui2_favorites.json), грузится лениво, пишется на изменении.
 */
public final class FavoriteStore {
    private static final Gson GSON = new Gson();
    private static final Set<String> favorites = new HashSet<>();
    private static boolean loaded;

    private FavoriteStore() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("collective").resolve("gui2_favorites.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = file();
            if (Files.exists(path)) {
                Set<String> data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                        new TypeToken<Set<String>>() {
                        }.getType());
                if (data != null) {
                    favorites.addAll(data);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            favorites.clear();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(favorites), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean isFavorite(String moduleName) {
        ensureLoaded();
        return favorites.contains(moduleName);
    }

    public static void toggle(String moduleName) {
        ensureLoaded();
        if (!favorites.remove(moduleName)) {
            favorites.add(moduleName);
        }
        save();
    }
}
