package me.collective.addon;

import me.collective.command.ChatHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class AddonManager {
    public static final AddonManager INSTANCE = new AddonManager();

    private final List<CollectiveAddon> addons = new ArrayList<>();
    private final Map<CollectiveAddon, URLClassLoader> dynamicLoaders = new HashMap<>();
    private final Map<String, String> loadFailures = new HashMap<>();
    private final Path addonsFolder;
    private final Path addonsSrcFolder;

    private AddonManager() {
        this.addonsFolder = FabricLoader.getInstance().getGameDir().resolve("collective").resolve("addons");
        this.addonsSrcFolder = FabricLoader.getInstance().getGameDir().resolve("collective").resolve("addons-src");
        try {
            Files.createDirectories(addonsFolder);
            Files.createDirectories(addonsSrcFolder);
            writeDocs();
        } catch (Exception ignored) {}
    }

    public static AddonManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        loadFabricEntrypoints();
        loadDynamicAddons();
    }

    // --- Загрузка ---

    private void loadFabricEntrypoints() {
        for (EntrypointContainer<CollectiveAddon> container : FabricLoader.getInstance().getEntrypointContainers("collective", CollectiveAddon.class)) {
            ModMetadata meta = container.getProvider().getMetadata();
            try {
                CollectiveAddon addon = container.getEntrypoint();

                String author = "Unknown";
                if (!meta.getAuthors().isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (Person p : meta.getAuthors()) names.add(p.getName());
                    author = String.join(", ", names);
                }

                String website = meta.getContact().get("sources").or(() -> meta.getContact().get("homepage")).orElse("");
                addon.initMetadata(meta.getName(), meta.getVersion().getFriendlyString(), author, meta.getDescription(), website);

                if (findAddonByName(addon.getName()) != null) {
                    System.out.println("[Collective] Аддон '" + addon.getName() + "' уже загружен — пропускаю дубликат (fabric entrypoint)");
                    continue;
                }

                addon.onInitialize();
                addons.add(addon);
                System.out.println("[Collective] Loaded Fabric entrypoint addon: " + addon.getName() + " v" + addon.getVersion());
            } catch (Throwable t) {
                recordFailure("fabric:" + meta.getName(), summarize(t));
                System.err.println("[Collective] Failed to load Fabric addon " + meta.getName() + ": " + t);
                t.printStackTrace();
            }
        }
    }

    public void loadDynamicAddons() {
        File dir = addonsFolder.toFile();
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File jar : files) {
            loadJarAddon(jar);
        }
    }

    /**
     * Загружает все аддоны из jar: классы-наследники CollectiveAddon ищутся по всему архиву
     * (вложенные и служебные классы пропускаются). Возвращает true, если загрузился хотя бы один.
     */
    public boolean loadJarAddon(File jarFile) {
        String jarName = jarFile.getName();
        URLClassLoader loader = null;
        try {
            List<Class<?>> targets = new ArrayList<>();
            loader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, getClass().getClassLoader());

            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entry.isDirectory() || !entryName.endsWith(".class")) continue;
                    if (entryName.startsWith("META-INF/")) continue;

                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                    if (className.contains("$")) continue;                                     // вложенные/анонимные классы
                    if (className.equals("module-info") || className.endsWith("package-info")) continue;

                    try {
                        Class<?> cls = Class.forName(className, false, loader);
                        if (CollectiveAddon.class.isAssignableFrom(cls)
                                && !cls.isInterface()
                                && !Modifier.isAbstract(cls.getModifiers())) {
                            targets.add(cls);
                        }
                    } catch (Throwable ignored) {
                        // Класс не читается (нет зависимостей) — пропускаем
                    }
                }
            }

            if (targets.isEmpty()) {
                loader.close();
                recordFailure(jarName, "класс-аддон не найден (нужен неабстрактный наследник CollectiveAddon)");
                return false;
            }

            List<String> errors = new ArrayList<>();
            boolean anyLoaded = false;
            for (Class<?> target : targets) {
                try {
                    CollectiveAddon addon = (CollectiveAddon) target.getDeclaredConstructor().newInstance();

                    if (findAddonByName(addon.getName()) != null) {
                        System.out.println("[Collective] Аддон '" + addon.getName() + "' уже загружен — пропускаю дубликат из " + jarName);
                        continue;
                    }

                    AddonInfo info = target.getAnnotation(AddonInfo.class);
                    if (info != null) {
                        addon.initMetadata(info.name(), info.version(), info.author(), info.description(), info.website());
                    } else {
                        addon.initMetadata(target.getSimpleName(), "1.0.0", "Dynamic Addon", "", "");
                    }

                    addon.onInitialize();
                    addons.add(addon);
                    dynamicLoaders.put(addon, loader);
                    anyLoaded = true;
                    System.out.println("[Collective] Loaded dynamic addon from JAR: " + addon.getName() + " v" + addon.getVersion() + " (" + jarName + ")");
                } catch (Throwable t) {
                    errors.add(target.getSimpleName() + ": " + summarize(t));
                    System.err.println("[Collective] Failed to init addon class " + target.getSimpleName() + " from " + jarName + ": " + t);
                }
            }

            if (anyLoaded) {
                loadFailures.remove(jarName);
                if (!errors.isEmpty()) {
                    System.err.println("[Collective] Часть классов из " + jarName + " не загрузилась: " + String.join("; ", errors));
                }
                return true;
            }

            loader.close();
            recordFailure(jarName, errors.isEmpty() ? "аддон не загружен" : String.join("; ", errors));
            return false;
        } catch (Throwable t) {
            if (loader != null) {
                try {
                    loader.close();
                } catch (Exception ignored) {}
            }
            recordFailure(jarName, summarize(t));
            System.err.println("[Collective] Failed to load jar addon " + jarName + ": " + t);
            return false;
        }
    }

    // --- Выгрузка ---

    /** Выгружает аддон по имени: вызывает onShutdown, снимает модули/команды и закрывает загрузчик. */
    public boolean unloadAddon(String name) {
        CollectiveAddon addon = findAddonByName(name);
        if (addon == null) return false;

        try {
            addon.onShutdown();
        } catch (Throwable t) {
            System.err.println("[Collective] Ошибка выгрузки аддона " + addon.getName() + ": " + t);
        }
        addons.remove(addon);

        URLClassLoader loader = dynamicLoaders.remove(addon);
        if (loader != null && !dynamicLoaders.containsValue(loader)) {
            try {
                loader.close();
            } catch (Exception ignored) {}
        }
        System.out.println("[Collective] Unloaded addon: " + addon.getName());
        return true;
    }

    public void reload() {
        for (CollectiveAddon addon : new ArrayList<>(addons)) {
            try {
                addon.onShutdown();
            } catch (Throwable ignored) {}
            URLClassLoader loader = dynamicLoaders.remove(addon);
            if (loader != null && !dynamicLoaders.containsValue(loader)) {
                try {
                    loader.close();
                } catch (Exception ignored) {}
            }
        }
        addons.clear();
        dynamicLoaders.clear();
        loadFailures.clear();
        init();

        int failed = loadFailures.size();
        ChatHelper.sendMessage(Component.literal("§a[Collective] Аддоны перезагружены! Загружено: §f" + addons.size()
                + (failed > 0 ? " §7(§cне загрузилось: " + failed + "§7, подробности: §f.addon list§7)" : "")));
    }

    // --- Поиск и состояние ---

    /** Найти загруженный аддон по имени (без учёта регистра). */
    public CollectiveAddon getAddon(String name) {
        return findAddonByName(name);
    }

    private CollectiveAddon findAddonByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (CollectiveAddon addon : addons) {
            if (addon.getName().equalsIgnoreCase(name.trim())) return addon;
        }
        return null;
    }

    public List<CollectiveAddon> getAddons() {
        return Collections.unmodifiableList(addons);
    }

    public Path getAddonsFolder() {
        return addonsFolder;
    }

    /** Ошибки загрузки: имя jar-файла (или fabric:<имя>) -> причина. */
    public Map<String, String> getLoadFailures() {
        return Collections.unmodifiableMap(loadFailures);
    }

    private void recordFailure(String source, String reason) {
        loadFailures.put(source, reason == null || reason.isBlank() ? "неизвестная ошибка" : reason);
    }

    private static String summarize(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) msg = t.toString();
        int nl = msg.indexOf('\n');
        if (nl > 0) msg = msg.substring(0, nl);
        if (msg.length() > 160) msg = msg.substring(0, 157) + "...";
        return msg;
    }

    // --- Исходники аддонов (collective/addons-src) — используются MCP и .addon build ---

    public Path getAddonsSrcFolder() {
        try {
            Files.createDirectories(addonsSrcFolder);
        } catch (Exception ignored) {}
        return addonsSrcFolder;
    }

    /** Санитайз имени исходника: без путей, с расширением .java. */
    public static String sanitizeJavaName(String name) {
        if (name == null) return null;
        String clean = name.trim().replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) clean = clean.substring(slash + 1);
        clean = clean.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        if (clean.contains("..")) clean = clean.replace("..", "_");
        if (clean.isEmpty()) return null;
        if (!clean.toLowerCase(Locale.ROOT).endsWith(".java")) clean = clean + ".java";
        return clean;
    }

    /** Сохранить исходник аддона. Возвращает путь к файлу. */
    public Path saveAddonSource(String name, String content) throws IOException {
        String fileName = sanitizeJavaName(name);
        if (fileName == null) throw new IOException("Недопустимое имя исходника аддона");
        Path folder = getAddonsSrcFolder();
        Path file = folder.resolve(fileName);
        Files.writeString(file, content == null ? "" : content);
        return file;
    }

    /** Имена файлов-исходников в collective/addons-src. */
    public List<String> listAddonSources() {
        try (java.util.stream.Stream<Path> stream = Files.list(getAddonsSrcFolder())) {
            List<String> names = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".java"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(names::add);
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Путь к исходнику аддона (null, если файла нет). */
    public Path getAddonSource(String name) {
        String fileName = sanitizeJavaName(name);
        if (fileName == null) return null;
        Path direct = getAddonsSrcFolder().resolve(fileName);
        return Files.isRegularFile(direct) ? direct : null;
    }

    public boolean deleteAddonSource(String name) {
        Path file = getAddonSource(name);
        if (file == null) return false;
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // --- Документация в папке аддонов ---

    private void writeDocs() {
        try {
            Path readme = addonsFolder.resolve("README.txt");
            if (!Files.exists(readme)) Files.writeString(readme, ADDONS_README);

            Path example = addonsFolder.resolve("ExampleAddon.java.example");
            if (!Files.exists(example)) Files.writeString(example, EXAMPLE_ADDON_SOURCE);
        } catch (Exception e) {
            System.err.println("[Collective] Не удалось создать документацию аддонов: " + e);
        }
    }

    private static final String ADDONS_README = """
            Collective Client — аддоны
            ===========================

            Как установить аддон:
              1. Готовый .jar аддона положите в эту папку (collective/addons/).
              2. Выполните .addon reload — аддон подхватится на лету (без перезапуска игры).

            Два способа сделать аддон:

              A) Динамический jar (проще всего):
                 - Напишите класс, наследующий me.collective.addon.CollectiveAddon,
                   с аннотацией @AddonInfo (имя, версия, автор, описание).
                 - Скомпилируйте его против классовой дорожки клиента и упакуйте в .jar
                   (достаточно самого класса, без fabric.mod.json).
                 - Через MCP это делают инструменты write_addon_source + build_addon,
                   либо команда .addon build <Имя> (мгновенная сборка работает, если
                   клиент запущен из dev-окружения Gradle; иначе соберите вручную).

              B) Обычный Fabric-мод:
                 - Соберите Fabric-мод и добавьте в fabric.mod.json:
                     "entrypoints": { "collective": [ "com.example.MyAddon" ] }
                 - Положите мод в папку mods/ как обычно.

            Пример кода аддона лежит рядом: ExampleAddon.java.example

            Управление (команда .addon):
              .addon list            — список загруженных аддонов и ошибок загрузки
              .addon load <jar>      — загрузить jar на лету (имя из папки или путь)
              .addon unload <имя>    — выгрузить аддон
              .addon info <имя>      — подробности об аддоне
              .addon sources         — исходники аддонов (collective/addons-src)
              .addon build <имя>     — собрать и загрузить исходник из collective/addons-src
              .addon reload          — перезагрузить все аддоны
              .addon folder          — открыть эту папку

            API аддона (внутри класса-наследника CollectiveAddon):
              registerModule(module)    — обычный модуль (Module)
              registerHud(hudElement)   — HUD-элемент (HudModule)
              registerCommand(command)  — команда чата
              subscribeEvents(this)     — подписка на события (@EventHandler), отписка: unsubscribeEvents(this)
              getModuleManager(), getCommandManager(), getEventBus(),
              getRotations(), getFontManager(), getBlurEngine()
            """;

    private static final String EXAMPLE_ADDON_SOURCE = """
            package com.example.addon;

            import me.collective.addon.AddonInfo;
            import me.collective.addon.CollectiveAddon;
            import me.collective.category.Category;
            import me.collective.module.Module;
            import me.collective.setting.BooleanSetting;

            @AddonInfo(
                    name = "ExampleAddon",
                    version = "1.0.0",
                    author = "YourName",
                    description = "Пример аддона для Collective Client"
            )
            public class ExampleAddon extends CollectiveAddon {

                @Override
                public void onInitialize() {
                    registerModule(new ExampleModule());
                    System.out.println("[ExampleAddon] Загружен!");
                }

                @Override
                public void onShutdown() {
                    System.out.println("[ExampleAddon] Выгружен!");
                    super.onShutdown();
                }

                /** Пример модуля аддона. */
                public static class ExampleModule extends Module {
                    public final BooleanSetting demoSettings = add(new BooleanSetting("Демо-настройка", true));

                    public ExampleModule() {
                        super("ExampleModule", "Пример модуля из аддона", Category.MISC);
                    }
                }
            }
            """;
}