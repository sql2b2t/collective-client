package me.collective.proxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Список прокси и активное подключение. Файл collective/proxies.json.
 * activeIndex < 0 — прямое подключение без прокси.
 */
public class ProxyManager {
    public static final ProxyManager INSTANCE = new ProxyManager();

    private final Path proxiesFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<ProxyEntry> proxies = new ArrayList<>();
    private int activeIndex = -1;
    private boolean enabled = true;

    private ProxyManager() {
        this.proxiesFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getGameDir().resolve("collective").resolve("proxies.json");
        load();
    }

    public static ProxyManager getInstance() {
        return INSTANCE;
    }

    public List<ProxyEntry> getProxies() {
        return proxies;
    }

    public synchronized void load() {
        proxies.clear();
        activeIndex = -1;
        try {
            if (!Files.exists(proxiesFile)) return;
            JsonObject root = gson.fromJson(Files.readString(proxiesFile, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            if (root.has("proxies") && root.get("proxies").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("proxies")) {
                    JsonObject o = e.getAsJsonObject();
                    ProxyEntry.ProxyType type = ProxyEntry.ProxyType.SOCKS5;
                    try {
                        type = ProxyEntry.ProxyType.valueOf(o.get("type").getAsString().toUpperCase());
                    } catch (Exception ignored) {
                    }
                    proxies.add(new ProxyEntry(type,
                        o.get("host").getAsString(), o.get("port").getAsInt(),
                        o.has("user") ? o.get("user").getAsString() : "",
                        o.has("pass") ? o.get("pass").getAsString() : ""));
                }
            }
            if (root.has("active")) {
                int idx = root.get("active").getAsInt();
                if (idx >= 0 && idx < proxies.size()) activeIndex = idx;
            }
            if (root.has("enabled")) {
                enabled = root.get("enabled").getAsBoolean();
            }
        } catch (Exception e) {
            System.err.println("[Collective-Proxy] Load failed: " + e.getMessage());
        }
    }

    public synchronized boolean save() {
        try {
            Files.createDirectories(proxiesFile.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("active", activeIndex);
            root.addProperty("enabled", enabled);
            JsonArray arr = new JsonArray();
            for (ProxyEntry p : proxies) {
                JsonObject o = new JsonObject();
                o.addProperty("type", p.type().name());
                o.addProperty("host", p.host());
                o.addProperty("port", p.port());
                o.addProperty("user", p.user());
                o.addProperty("pass", p.pass());
                arr.add(o);
            }
            root.add("proxies", arr);
            Files.writeString(proxiesFile, gson.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            System.err.println("[Collective-Proxy] Save failed: " + e.getMessage());
            return false;
        }
    }

    public boolean addLine(String line) {
        ProxyEntry entry = ProxyEntry.parse(line);
        if (entry == null) return false;
        proxies.add(entry);
        return save();
    }

    public boolean remove(int index) {
        if (index < 0 || index >= proxies.size()) return false;
        proxies.remove(index);
        if (activeIndex == index) activeIndex = -1;
        else if (activeIndex > index) activeIndex--;
        return save();
    }

    public void setActive(int index) {
        if (index >= 0 && index < proxies.size()) activeIndex = index;
        else activeIndex = -1;
        save();
    }

    public void setDirect() {
        activeIndex = -1;
        save();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** null — прямое подключение. */
    public ProxyEntry getActive() {
        if (activeIndex >= 0 && activeIndex < proxies.size()) return proxies.get(activeIndex);
        return null;
    }

    public String activeName() {
        ProxyEntry active = getActive();
        return active == null ? "Прямое" : active.displayName();
    }
}
