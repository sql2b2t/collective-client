package me.collective.gui.alt;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Асинхронный загрузчик и дисковый кеш реальных скинов игроков Minecraft (Mojang API + Minotar CDN).
 * Загружает оригинальные текстуры скинов и кеширует их на диск и в память OpenGL.
 */
public final class AltSkinHelper {

    private static final Path SKINS_DIR = FabricLoader.getInstance()
            .getGameDir().resolve("collective").resolve("skins");

    private static final Map<String, Identifier> CUSTOM_SKINS = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<SkinTextures>> SUPPLIERS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Collective-SkinLoader");
        t.setDaemon(true);
        return t;
    });

    private AltSkinHelper() {}

    /**
     * Отрисовка головы игрока с поддержкой реальных скинов и слоя шапки.
     */
    public static void drawSkinHead(GuiGraphics ctx, String username, UUID uuid, int x, int y, int size) {
        if (username == null || username.isBlank()) {
            PlayerSkinDrawer.draw(ctx, DefaultSkinHelper.getSkinTextures(uuid != null ? uuid : UUID.randomUUID()), x, y, size);
            return;
        }

        String key = username.trim().toLowerCase();

        // 1. Быстрый рендер из кеша загруженных текстур
        Identifier customId = CUSTOM_SKINS.get(key);
        if (customId != null) {
            try {
                PlayerSkinDrawer.draw(ctx, customId, x, y, size, true, false, -1);
                return;
            } catch (Throwable ignored) {}
        }

        // 2. Инициируем асинхронную загрузку при первом запросе
        ensureLoading(username, uuid);

        // 3. Если готов Supplier от Mojang PlayerSkinProvider
        Supplier<SkinTextures> sup = SUPPLIERS.get(key);
        if (sup != null) {
            try {
                SkinTextures textures = sup.get();
                if (textures != null) {
                    PlayerSkinDrawer.draw(ctx, textures, x, y, size);
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 4. Fallback на Steve/Alex
        UUID safeUuid = uuid != null ? uuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        PlayerSkinDrawer.draw(ctx, DefaultSkinHelper.getSkinTextures(safeUuid), x, y, size);
    }

    private static void ensureLoading(String username, UUID uuid) {
        String key = username.trim().toLowerCase();
        if (CUSTOM_SKINS.containsKey(key) || !PENDING.add(key)) {
            return;
        }

        POOL.execute(() -> {
            try {
                Files.createDirectories(SKINS_DIR);
                Path cachedFile = SKINS_DIR.resolve(key + ".png");

                // Проверяем локальный дисковый кеш
                if (Files.exists(cachedFile)) {
                    try (InputStream is = Files.newInputStream(cachedFile)) {
                        NativeImage img = NativeImage.read(is);
                        registerNativeSkin(key, img);
                        return;
                    } catch (Throwable ignored) {}
                }

                // 1. Скачиваем скин напрямую с Minotar CDN (высокоскоростной Cloudflare кеш)
                if (downloadSkinFromMinotar(username, cachedFile)) {
                    return;
                }

                // 2. Резервный поиск официального Mojang UUID
                UUID realMojangUuid = resolveMojangUuid(username);
                if (realMojangUuid != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.getSkinProvider() != null) {
                        Supplier<SkinTextures> sup = mc.getSkinProvider()
                                .supplySkinTextures(new GameProfile(realMojangUuid, username), false);
                        SUPPLIERS.put(key, sup);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                PENDING.remove(key);
            }
        });
    }

    private static boolean downloadSkinFromMinotar(String username, Path cachedFile) {
        try {
            URI uri = URI.create("https://minotar.net/skin/" + username.trim());
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    NativeImage img = NativeImage.read(is);
                    try {
                        img.writeTo(cachedFile);
                    } catch (Throwable ignored) {}
                    registerNativeSkin(username.trim().toLowerCase(), img);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void registerNativeSkin(String key, NativeImage img) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "alt_skin_" + key, img);
                tex.upload();
                Identifier id = Identifier.of("collective", "skins/" + key);
                mc.getTextureManager().registerTexture(id, tex);
                CUSTOM_SKINS.put(key, id);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static UUID resolveMojangUuid(String username) {
        try {
            URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + username.trim());
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Minecraft");

            if (conn.getResponseCode() == 200) {
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int idIdx = body.indexOf("\"id\"");
                if (idIdx != -1) {
                    int start = body.indexOf('"', idIdx + 4) + 1;
                    int end = body.indexOf('"', start);
                    String rawId = body.substring(start, end);
                    return UUID.fromString(
                            rawId.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")
                    );
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}