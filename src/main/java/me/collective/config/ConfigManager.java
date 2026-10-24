package me.collective.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.collective.command.ChatHelper;
import me.collective.command.CommandManager;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.hud.HudModule;
import me.collective.setting.ActionSetting;
import me.collective.setting.BlockListSetting;
import me.collective.setting.ChoiceSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.DynamicActionSetting;
import me.collective.setting.EntityListSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.IntegerSetting;
import me.collective.setting.Keybind;
import me.collective.setting.KeybindSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.Setting;
import me.collective.setting.SettingColorSetting;
import me.collective.setting.StringSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Система конфигов клиента.
 *
 * <p>Хранит в {@code .minecraft/collective/configs/*.json}: вкл/выкл модулей,
 * все их настройки, позиции HUD-элементов, префикс команд и список друзей.
 * {@code default.json} грузится при старте и автосохраняется при изменениях,
 * поэтому настройки больше не сбрасываются после перезахода.
 */
public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();
    public static final String DEFAULT_NAME = "default";

    /** Пауза автосейва после последнего изменения (по умолчанию 3 мин). */
    private long autosaveMs = 180_000L;

    private final Path configsFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean dirty = false;
    private long lastSave = System.currentTimeMillis();

    private ConfigManager() {
        this.configsFolder = FabricLoader.getInstance().getGameDir().resolve("collective").resolve("configs");
        try {
            Files.createDirectories(configsFolder);
        } catch (Exception ignored) {
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dirty) {
                save(DEFAULT_NAME);
            }
        }, "Collective-Config-Shutdown"));
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public Path getConfigsFolder() {
        return configsFolder;
    }

    /** Помечает конфиг изменённым (вызывается из настроек и модулей). */
    public void markDirty() {
        dirty = true;
    }

    /** Принудительно запрашивает сохранение текущей конфигурации. */
    public void requestSave() {
        markDirty();
        save(DEFAULT_NAME);
    }

    /** Вызывается каждый клиентский тик: автосейв default при изменениях. */
    public void tick() {
        if (!dirty) return;
        try {
            me.collective.module.client.AutoSaveCfg m = me.collective.module.ModuleManager.INSTANCE
                .getModule(me.collective.module.client.AutoSaveCfg.class);
            if (m != null && !m.isToggled()) return;
        } catch (Throwable ignored) {
        }
        if (System.currentTimeMillis() - lastSave < autosaveMs) return;
        save(DEFAULT_NAME);
    }

    public long getAutosaveMs() {
        return autosaveMs;
    }

    public void setAutosaveMs(long autosaveMs) {
        this.autosaveMs = Math.max(10_000L, autosaveMs);
    }

    public File fileOf(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_\\- ]", "_").trim();
        if (safe.isEmpty()) safe = DEFAULT_NAME;
        return configsFolder.resolve(safe + ".json").toFile();
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        File[] files = configsFolder.toFile().listFiles((dir, n) -> n.endsWith(".json"));
        if (files == null) return names;
        for (File f : files) {
            String n = f.getName();
            names.add(n.substring(0, n.length() - 5));
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    public synchronized boolean save(String name) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("prefix", CommandManager.INSTANCE.getPrefix());

            JsonArray friends = new JsonArray();
            for (String f : FriendManager.INSTANCE.getFriends()) friends.add(f);
            root.add("friends", friends);

            JsonObject modules = new JsonObject();
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                JsonObject mj = new JsonObject();
                boolean isUi = (m instanceof me.collective.module.client.UI);
                mj.addProperty("toggled", isUi ? false : m.isToggled());
                if (m instanceof HudModule hud) {
                    mj.addProperty("x", hud.getX());
                    mj.addProperty("y", hud.getY());
                }
                JsonObject settings = new JsonObject();
                for (Setting<?> s : m.getSettings()) {
                    JsonElement v = serializeSetting(s);
                    if (v != null) settings.add(s.getName(), v);
                }
                mj.add("settings", settings);
                modules.add(m.getName(), mj);
            }
            root.add("modules", modules);

            Files.writeString(fileOf(name).toPath(), gson.toJson(root));
            dirty = false;
            lastSave = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            System.err.println("[Collective-Config] Save failed (" + name + "): " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean load(String name) {
        File f = fileOf(name);
        if (!f.isFile()) return false;
        try {
            JsonObject root = gson.fromJson(Files.readString(f.toPath()), JsonObject.class);
            if (root == null) return false;

            if (root.has("prefix")) {
                CommandManager.INSTANCE.setPrefix(root.get("prefix").getAsString());
            }

            if (root.has("friends") && root.get("friends").isJsonArray()) {
                java.util.ArrayList<String> loaded = new java.util.ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("friends")) {
                    if (e.isJsonPrimitive()) loaded.add(e.getAsString());
                }
                FriendManager.INSTANCE.setFriends(loaded);
            }

            if (root.has("modules") && root.get("modules").isJsonObject()) {
                JsonObject modules = root.getAsJsonObject("modules");
                for (Module m : ModuleManager.INSTANCE.getModules()) {
                    if (!modules.has(m.getName())) continue;
                    JsonObject mj = modules.getAsJsonObject(m.getName());
                    if (m instanceof HudModule hud && mj.has("x") && mj.has("y")) {
                        hud.setX(mj.get("x").getAsFloat());
                        hud.setY(mj.get("y").getAsFloat());
                    }
                    if (mj.has("settings") && mj.get("settings").isJsonObject()) {
                        JsonObject settings = mj.getAsJsonObject("settings");
                        for (Setting<?> s : m.getSettings()) {
                            if (!settings.has(s.getName())) continue;
                            try {
                                applySetting(s, settings.get(s.getName()));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (mj.has("toggled")) {
                        if (m instanceof me.collective.module.client.UI) {
                            m.setToggled(false);
                        } else {
                            m.setToggled(mj.get("toggled").getAsBoolean());
                        }
                    }
                }
                if (me.collective.module.client.UI.INSTANCE != null
                        && (me.collective.module.client.UI.INSTANCE.getKeybind().getValue() <= 0
                        || me.collective.module.client.UI.INSTANCE.getKeybind().getValue() == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN)) {
                    me.collective.module.client.UI.INSTANCE.getKeybind().setValue(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                }
            }

            dirty = false;
            return true;
        } catch (Exception e) {
            System.err.println("[Collective-Config] Load failed (" + name + "): " + e.getMessage());
            ChatHelper.sendMessage(Text.literal("§c[Config] Ошибка загрузки конфига: " + e.getMessage()));
            return false;
        }
    }

    public boolean loadDefault() {
        File f = fileOf(DEFAULT_NAME);
        if (!f.isFile()) return false;
        return load(DEFAULT_NAME);
    }

    private static JsonElement serializeSetting(Setting<?> s) {
        // Кнопки без состояния (ActionSetting/DynamicActionSetting) в конфиг не пишутся
        if (s instanceof ActionSetting || s instanceof DynamicActionSetting) return null;
        if (s instanceof SettingColorSetting scs) {
            me.collective.util.SettingColor sc = scs.getValue();
            if (sc == null) return null;
            JsonObject co = new JsonObject();
            co.addProperty("color", sc.toAwt().getRGB());
            return co;
        }
        // ChoiceSetting: строка + вложенные тумблеры опций
        if (s instanceof ChoiceSetting cs) {
            JsonObject o = new JsonObject();
            o.addProperty("value", String.valueOf(s.getValue()));
            JsonObject opts = new JsonObject();
            for (me.collective.setting.BooleanSetting opt : cs.getOptions()) {
                opts.addProperty(opt.getName(), opt.getValue());
            }
            o.add("options", opts);
            return o;
        }
        if (s instanceof EnumSetting<?> es) {
            Object v = es.getValue();
            return v == null ? null : new JsonPrimitive(v.toString());
        }
        if (s instanceof ColorSetting cs) {
            Object v = s.getValue();
            if (!(v instanceof Color c)) return null;
            // Радуга и синхронизация с темой — часть настройки цвета.
            com.google.gson.JsonObject co = new com.google.gson.JsonObject();
            co.addProperty("color", c.getRGB());
            if (cs.isRainbow()) co.addProperty("rainbow", true);
            if (cs.isSync()) co.addProperty("sync", true);
            if (cs.getRainbowSpeed() != 1.0f) co.addProperty("speed", cs.getRainbowSpeed());
            return co;
        }
        if (s instanceof BlockListSetting bls) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (net.minecraft.block.Block b : bls.getValue()) {
                arr.add(net.minecraft.registry.Registries.BLOCK.getId(b).toString());
            }
            return arr;
        }
        if (s instanceof EntityListSetting els) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (net.minecraft.entity.EntityType<?> type : els.getValue()) {
                arr.add(net.minecraft.registry.Registries.ENTITY_TYPE.getId(type).toString());
            }
            return arr;
        }
        if (s instanceof me.collective.setting.ItemListSetting ils) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (net.minecraft.item.Item item : ils.getValue()) {
                arr.add(net.minecraft.registry.Registries.ITEM.getId(item).toString());
            }
            return arr;
        }
        Object v = s.getValue();
        if (v instanceof Keybind k) return new JsonPrimitive(k.getKey());
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Integer i) return new JsonPrimitive(i);
        if (v instanceof Number n) return new JsonPrimitive(n.doubleValue());
        if (v instanceof String str) return new JsonPrimitive(str);
        if (v instanceof Color c) return new JsonPrimitive(c.getRGB());
        if (v instanceof me.collective.util.SettingColor sc) return new JsonPrimitive(sc.toAwt().getRGB());
        // ActionSetting и прочие кнопки без состояния — не храним
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applySetting(Setting<?> s, JsonElement el) {
        if (s instanceof ActionSetting || s instanceof DynamicActionSetting) return;
        if (s instanceof SettingColorSetting scs) {
            if (el.isJsonObject() && el.getAsJsonObject().has("color")) {
                scs.setValue(new me.collective.util.SettingColor(new Color(el.getAsJsonObject().get("color").getAsInt(), true)));
            } else if (el.isJsonPrimitive()) {
                scs.setValue(new me.collective.util.SettingColor(new Color(el.getAsInt(), true)));
            }
            return;
        }
        if (s instanceof ChoiceSetting cs && el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("value")) ((Setting) s).setValue(o.get("value").getAsString());
            if (o.has("options") && o.get("options").isJsonObject()) {
                JsonObject opts = o.getAsJsonObject("options");
                for (me.collective.setting.BooleanSetting opt : cs.getOptions()) {
                    if (opts.has(opt.getName())) opt.setValue(opts.get(opt.getName()).getAsBoolean());
                }
            }
            return;
        }
        if (s instanceof BlockListSetting bls && el.isJsonArray()) {
            java.util.List<net.minecraft.block.Block> list = new java.util.ArrayList<>();
            for (var item : el.getAsJsonArray()) {
                try {
                    net.minecraft.block.Block b = net.minecraft.registry.Registries.BLOCK.get(
                        net.minecraft.util.Identifier.of(item.getAsString()));
                    if (b != null && b != net.minecraft.block.Blocks.AIR) list.add(b);
                } catch (Exception ignored) {
                }
            }
            ((Setting<List<?>>) s).setValue(list);
            return;
        }
        if (s instanceof EntityListSetting els && el.isJsonArray()) {
            java.util.List<net.minecraft.entity.EntityType<?>> list = new java.util.ArrayList<>();
            for (var item : el.getAsJsonArray()) {
                try {
                    net.minecraft.entity.EntityType<?> type = net.minecraft.registry.Registries.ENTITY_TYPE.get(
                        net.minecraft.util.Identifier.of(item.getAsString()));
                    if (type != null) list.add(type);
                } catch (Exception ignored) {
                }
            }
            ((Setting<List<?>>) s).setValue(list);
            return;
        }
        if (s instanceof me.collective.setting.ItemListSetting ils && el.isJsonArray()) {
            java.util.List<net.minecraft.item.Item> list = new java.util.ArrayList<>();
            for (var item : el.getAsJsonArray()) {
                try {
                    net.minecraft.item.Item it = net.minecraft.registry.Registries.ITEM.get(
                        net.minecraft.util.Identifier.of(item.getAsString()));
                    if (it != null && it != net.minecraft.item.Items.AIR) list.add(it);
                } catch (Exception ignored) {
                }
            }
            ((Setting<List<?>>) s).setValue(list);
            return;
        }
        if (s instanceof ColorSetting cs && el.isJsonObject()) {
            com.google.gson.JsonObject co = el.getAsJsonObject();
            if (co.has("color")) cs.setValue(new Color(co.get("color").getAsInt(), true));
            cs.setRainbow(co.has("rainbow") && co.get("rainbow").getAsBoolean());
            cs.setSync(co.has("sync") && co.get("sync").getAsBoolean());
            if (co.has("speed")) cs.setRainbowSpeed(co.get("speed").getAsFloat());
            return;
        }
        if (!el.isJsonPrimitive()) return;
        if (s instanceof EnumSetting es) {
            es.setByName(el.getAsString());
            return;
        }
        if (s instanceof ColorSetting) {
            ((Setting) s).setValue(new Color(el.getAsInt(), true));
            return;
        }
        if (s instanceof KeybindSetting) {
            ((Setting) s).setValue(el.getAsInt());
            return;
        }
        if (s instanceof IntegerSetting is) {
            is.setValue(el.getAsInt());
            return;
        }
        if (s instanceof NumberSetting) {
            ((Setting) s).setValue(el.getAsDouble());
            return;
        }
        if (s instanceof ModeSetting || s instanceof StringSetting) {
            ((Setting) s).setValue(el.getAsString());
            return;
        }
        Object cur = s.getValue();
        if (cur instanceof Keybind) {
            ((Setting) s).setValue(new Keybind(el.getAsInt()));
        } else if (cur instanceof Boolean) {
            ((Setting) s).setValue(el.getAsBoolean());
        } else if (cur instanceof Integer) {
            ((Setting) s).setValue(el.getAsInt());
        } else if (cur instanceof Number) {
            ((Setting) s).setValue(el.getAsDouble());
        } else if (cur instanceof String) {
            ((Setting) s).setValue(el.getAsString());
        } else if (cur instanceof Color) {
            ((Setting) s).setValue(new Color(el.getAsInt(), true));
        } else if (cur instanceof me.collective.util.SettingColor) {
            ((Setting) s).setValue(new me.collective.util.SettingColor(new Color(el.getAsInt(), true)));
        }
    }
}
