package me.collective.addon

import me.collective.client.utils.chat.ChatUtils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Locale
import java.util.jar.JarFile

object AddonManager {
    private val LOG = LoggerFactory.getLogger(AddonManager::class.java)

    private val _addons: MutableList<CollectiveAddon> = ArrayList()
    private val dynamicLoaders: MutableMap<CollectiveAddon, URLClassLoader> = HashMap()
    private val _loadFailures: MutableMap<String, String> = HashMap()

    val addonsFolder: Path = FabricLoader.getInstance().gameDir.resolve("collective").resolve("addons")
    private val _addonsSrcFolder: Path = FabricLoader.getInstance().gameDir.resolve("collective").resolve("addons-src")

    val addonsSrcFolder: Path
        get() {
            try {
                Files.createDirectories(_addonsSrcFolder)
            } catch (_: Exception) {
            }
            return _addonsSrcFolder
        }

    init {
        try {
            Files.createDirectories(addonsFolder)
            Files.createDirectories(addonsSrcFolder)
            writeDocs()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun getInstance(): AddonManager = this

    fun init() {
        loadFabricEntrypoints()
        loadDynamicAddons()
    }

    // --- Загрузка ---

    private fun loadFabricEntrypoints() {
        for (container in FabricLoader.getInstance().getEntrypointContainers("collective", CollectiveAddon::class.java)) {
            val meta = container.provider.metadata
            try {
                val addon = container.entrypoint

                var author = "Unknown"
                if (meta.authors.isNotEmpty()) {
                    val names = ArrayList<String>()
                    for (p in meta.authors) names.add(p.getName())
                    author = names.joinToString(", ")
                }

                val website = meta.contact["sources"].orElseGet { meta.contact["homepage"].orElse("") }
                addon.initMetadata(meta.name, meta.version.friendlyString, author, meta.description, website)

                if (findAddonByName(addon.name) != null) {
                    LOG.info("[Collective] Аддон '${addon.name}' уже загружен — пропускаю дубликат (fabric entrypoint)")
                    continue
                }

                addon.onInitialize()
                _addons.add(addon)
                LOG.info("[Collective] Loaded Fabric entrypoint addon: ${addon.name} v${addon.version}")
            } catch (t: Throwable) {
                recordFailure("fabric:${meta.name}", summarize(t))
                LOG.error("[Collective] Failed to load Fabric addon ${meta.name}: $t")
                t.printStackTrace()
            }
        }
    }

    fun loadDynamicAddons() {
        val dir = addonsFolder.toFile()
        if (!dir.exists() || !dir.isDirectory) return

        val files = dir.listFiles { _, name -> name.endsWith(".jar") } ?: return
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        for (jar in files) {
            loadJarAddon(jar)
        }
    }

    /**
     * Загружает все аддоны из jar: классы-наследники CollectiveAddon ищутся по всему архиву
     * (вложенные и служебные классы пропускаются). Возвращает true, если загрузился хотя бы один.
     */
    fun loadJarAddon(jarFile: File): Boolean {
        val jarName = jarFile.name
        var loader: URLClassLoader? = null
        return try {
            val targets = ArrayList<Class<*>>()
            loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), AddonManager::class.java.classLoader)

            JarFile(jarFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    if (entry.isDirectory || !entryName.endsWith(".class")) continue
                    if (entryName.startsWith("META-INF/")) continue

                    val className = entryName.substring(0, entryName.length - 6).replace('/', '.')
                    if (className.contains("$")) continue // вложенные/анонимные классы
                    if (className == "module-info" || className.endsWith("package-info")) continue

                    try {
                        val cls = Class.forName(className, false, loader)
                        if (CollectiveAddon::class.java.isAssignableFrom(cls) && !cls.isInterface && !Modifier.isAbstract(cls.modifiers)) {
                            targets.add(cls)
                        }
                    } catch (_: Throwable) {
                        // Класс не читается (нет зависимостей) — пропускаем
                    }
                }
            }

            if (targets.isEmpty()) {
                loader.close()
                recordFailure(jarName, "класс-аддон не найден (нужен неабстрактный наследник CollectiveAddon)")
                return false
            }

            val errors = ArrayList<String>()
            var anyLoaded = false
            var anySkippedDuplicate = false
            for (target in targets) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val addon = target.getDeclaredConstructor().newInstance() as CollectiveAddon

                    val info = target.getAnnotation(AddonInfo::class.java)
                    if (info != null) {
                        addon.initMetadata(info.name, info.version, info.author, info.description, info.website)
                    } else {
                        addon.initMetadata(target.simpleName, "1.0.0", "Dynamic Addon", "", "")
                    }

                    // Проверка дубликата ПОСЛЕ initMetadata: имя берётся из @AddonInfo,
                    // иначе сравнение идёт с именем класса и дубликаты не отлавливаются.
                    if (findAddonByName(addon.name) != null) {
                        LOG.info("[Collective] Аддон '${addon.name}' уже загружен — пропускаю дубликат из $jarName")
                        anySkippedDuplicate = true
                        continue
                    }

                    addon.onInitialize()
                    _addons.add(addon)
                    dynamicLoaders[addon] = loader
                    anyLoaded = true
                    LOG.info("[Collective] Loaded dynamic addon from JAR: ${addon.name} v${addon.version} ($jarName)")
                } catch (t: Throwable) {
                    errors.add("${target.simpleName}: ${summarize(t)}")
                    LOG.error("[Collective] Failed to init addon class ${target.simpleName} from $jarName: $t")
                }
            }

            if (anyLoaded) {
                _loadFailures.remove(jarName)
                if (errors.isNotEmpty()) {
                    LOG.error("[Collective] Часть классов из $jarName не загрузилась: ${errors.joinToString("; ")}")
                }
                return true
            }

            loader.close()
            if (anySkippedDuplicate && errors.isEmpty()) {
                // Все классы оказались дубликатами уже загруженных аддонов — это не ошибка.
                _loadFailures.remove(jarName)
                return false
            }
            recordFailure(jarName, if (errors.isEmpty()) "аддон не загружен" else errors.joinToString("; "))
            false
        } catch (t: Throwable) {
            if (loader != null) {
                try {
                    loader.close()
                } catch (_: Exception) {
                }
            }
            recordFailure(jarName, summarize(t))
            LOG.error("[Collective] Failed to load jar addon $jarName: $t")
            false
        }
    }

    // --- Выгрузка ---

    /** Выгружает аддон по имени: вызывает onShutdown и закрывает загрузчик. */
    fun unloadAddon(name: String?): Boolean {
        val addon = findAddonByName(name) ?: return false

        try {
            addon.onShutdown()
        } catch (t: Throwable) {
            LOG.error("[Collective] Ошибка выгрузки аддона ${addon.name}: $t")
        }
        _addons.remove(addon)

        val loader = dynamicLoaders.remove(addon)
        if (loader != null && !dynamicLoaders.containsValue(loader)) {
            try {
                loader.close()
            } catch (_: Exception) {
            }
        }
        LOG.info("[Collective] Unloaded addon: ${addon.name}")
        return true
    }

    fun reload() {
        for (addon in ArrayList(_addons)) {
            try {
                addon.onShutdown()
            } catch (_: Throwable) {
            }
            val loader = dynamicLoaders.remove(addon)
            if (loader != null && !dynamicLoaders.containsValue(loader)) {
                try {
                    loader.close()
                } catch (_: Exception) {
                }
            }
        }
        _addons.clear()
        dynamicLoaders.clear()
        _loadFailures.clear()
        init()

        val failed = _loadFailures.size
        message(
            "§a[Collective] Аддоны перезагружены! Загружено: §f${_addons.size}" +
                if (failed > 0) " §7(§cне загрузилось: $failed§7, подробности: §f.addon list§7)" else ""
        )
    }

    // --- Поиск и состояние ---

    /** Найти загруженный аддон по имени (без учёта регистра). */
    fun getAddon(name: String?): CollectiveAddon? = findAddonByName(name)

    private fun findAddonByName(name: String?): CollectiveAddon? {
        if (name.isNullOrBlank()) return null
        val clean = name.trim()
        for (addon in _addons) {
            if (addon.name.equals(clean, ignoreCase = true)) return addon
        }
        return null
    }

    val addons: List<CollectiveAddon>
        get() = Collections.unmodifiableList(_addons)

    /** Ошибки загрузки: имя jar-файла (или fabric:<имя>) -> причина. */
    val loadFailures: Map<String, String>
        get() = Collections.unmodifiableMap(_loadFailures)

    private fun recordFailure(source: String, reason: String?) {
        _loadFailures[source] = if (reason.isNullOrBlank()) "неизвестная ошибка" else reason
    }

    private fun summarize(t: Throwable): String {
        var msg = t.message
        if (msg.isNullOrBlank()) msg = t.toString()
        val nl = msg.indexOf('\n')
        if (nl > 0) msg = msg.substring(0, nl)
        if (msg.length > 160) msg = msg.substring(0, 157) + "..."
        return msg
    }

    // --- Исходники аддонов (collective/addons-src) — используются .addon build ---

    /** Санитайз имени исходника: без путей, с расширением .java. */
    fun sanitizeJavaName(name: String?): String? {
        if (name == null) return null
        var clean = name.trim().replace('\\', '/')
        val slash = clean.lastIndexOf('/')
        if (slash >= 0) clean = clean.substring(slash + 1)
        clean = clean.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        if (clean.contains("..")) clean = clean.replace("..", "_")
        if (clean.isEmpty()) return null
        if (!clean.lowercase(Locale.ROOT).endsWith(".java")) clean += ".java"
        return clean
    }

    /** Сохранить исходник аддона. Возвращает путь к файлу. */
    @Throws(IOException::class)
    fun saveAddonSource(name: String?, content: String?): Path {
        val fileName = sanitizeJavaName(name) ?: throw IOException("Недопустимое имя исходника аддона")
        val folder = addonsSrcFolder
        val file = folder.resolve(fileName)
        Files.writeString(file, content ?: "")
        return file
    }

    /** Имена файлов-исходников в collective/addons-src. */
    fun listAddonSources(): List<String> {
        Files.list(addonsSrcFolder).use { stream ->
            val names = ArrayList<String>()
            stream.filter(Files::isRegularFile)
                .map { it.fileName.toString() }
                .filter { it.lowercase(Locale.ROOT).endsWith(".java") }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(names::add)
            return names
        }
    }

    /** Путь к исходнику аддона (null, если файла нет). */
    fun getAddonSource(name: String?): Path? {
        val fileName = sanitizeJavaName(name) ?: return null
        val direct = addonsSrcFolder.resolve(fileName)
        return if (Files.isRegularFile(direct)) direct else null
    }

    fun deleteAddonSource(name: String?): Boolean {
        val file = getAddonSource(name) ?: return false
        return try {
            Files.deleteIfExists(file)
            true
        } catch (_: IOException) {
            false
        }
    }

    // --- Документация в папке аддонов ---

    private fun writeDocs() {
        try {
            val readme = addonsFolder.resolve("README.txt")
            if (!Files.exists(readme)) Files.writeString(readme, ADDONS_README)

            val example = addonsFolder.resolve("ExampleAddon.java.example")
            if (!Files.exists(example)) Files.writeString(example, EXAMPLE_ADDON_SOURCE)
        } catch (e: Exception) {
            LOG.error("[Collective] Не удалось создать документацию аддонов: $e")
        }
    }

    /** Сообщение в чат с префиксом клиента. */
    fun message(text: String) {
        ChatUtils.printChat(Component.literal(text), ChatUtils.createSignature(-1))
    }

    private const val ADDONS_README: String = "Collective Client — аддоны\n" +
        "===========================\n" +
        "\n" +
        "Как установить аддон:\n" +
        "  1. Готовый .jar аддона положите в эту папку (collective/addons/).\n" +
        "  2. Выполните .addon reload — аддон подхватится на лету (без перезапуска игры).\n" +
        "\n" +
        "Два способа сделать аддон:\n" +
        "\n" +
        "  A) Динамический jar (проще всего):\n" +
        "     - Напишите класс, наследующий me.collective.addon.CollectiveAddon,\n" +
        "       с аннотацией @AddonInfo (имя, версия, автор, описание).\n" +
        "     - Скомпилируйте его против классовой дорожки клиента и упакуйте в .jar\n" +
        "       (достаточно самого класса, без fabric.mod.json).\n" +
        "     - Команда .addon build <Имя> собирает исходник из collective/addons-src\n" +
        "       (мгновенная сборка работает, если клиент запущен из dev-окружения\n" +
        "       Gradle; иначе соберите вручную).\n" +
        "\n" +
        "  B) Обычный Fabric-мод:\n" +
        "     - Соберите Fabric-мод и добавьте в fabric.mod.json:\n" +
        "         \"entrypoints\": { \"collective\": [ \"com.example.MyAddon\" ] }\n" +
        "     - Положите мод в папку mods/ как обычно.\n" +
        "\n" +
        "Пример кода аддона лежит рядом: ExampleAddon.java.example\n" +
        "\n" +
        "Управление (команда .addon):\n" +
        "  .addon list            — список загруженных аддонов и ошибок загрузки\n" +
        "  .addon load <jar>      — загрузить jar на лету (имя из папки или путь)\n" +
        "  .addon unload <имя>    — выгрузить аддон\n" +
        "  .addon info <имя>      — подробности об аддоне\n" +
        "  .addon sources         — исходники аддонов (collective/addons-src)\n" +
        "  .addon build <имя>     — собрать и загрузить исходник из collective/addons-src\n" +
        "  .addon reload          — перезагрузить все аддоны\n" +
        "  .addon folder          — открыть эту папку\n" +
        "\n" +
        "API аддона (внутри класса-наследника CollectiveAddon):\n" +
        "  registerModule(module)    — обычный модуль (Module)\n" +
        "  registerHud(hudElement)   — HUD-элемент (HudWidgetModule)\n" +
        "  registerCommand(command)  — команда чата (brigadier Command)\n" +
        "  subscribeEvents(this)     — подписка на события (@EventHandler), отписка: unsubscribeEvents(this)\n" +
        "  getModuleRegistry(), getCommandRegistry(), getEventBus(),\n" +
        "  getRotations(), getBaritone()\n"

    private const val EXAMPLE_ADDON_SOURCE: String = "package com.example.addon;\n" +
        "\n" +
        "import me.collective.addon.AddonInfo;\n" +
        "import me.collective.addon.CollectiveAddon;\n" +
        "import me.collective.client.modules.Module;\n" +
        "import me.collective.client.modules.ModuleCategory;\n" +
        "import me.collective.client.settings.BooleanSetting;\n" +
        "import org.slf4j.Logger;\n" +
        "import org.slf4j.LoggerFactory;\n" +
        "\n" +
        "@AddonInfo(\n" +
        "        name = \"ExampleAddon\",\n" +
        "        version = \"1.0.0\",\n" +
        "        author = \"YourName\",\n" +
        "        description = \"Пример аддона для Collective Client\"\n" +
        ")\n" +
        "public class ExampleAddon extends CollectiveAddon {\n" +
        "\n" +
        "    private static final Logger LOG = LoggerFactory.getLogger(ExampleAddon.class);\n" +
        "\n" +
        "    @Override\n" +
        "    public void onInitialize() {\n" +
        "        registerModule(new ExampleModule());\n" +
        "        LOG.info(\"[ExampleAddon] Загружен!\");\n" +
        "    }\n" +
        "\n" +
        "    @Override\n" +
        "    public void onShutdown() {\n" +
        "        LOG.info(\"[ExampleAddon] Выгружен!\");\n" +
        "        super.onShutdown();\n" +
        "    }\n" +
        "\n" +
        "    /** Пример модуля аддона. */\n" +
        "    public static class ExampleModule extends Module {\n" +
        "        public final BooleanSetting demoSettings = add(new BooleanSetting(\"Демо-настройка\", true));\n" +
        "\n" +
        "        public ExampleModule() {\n" +
        "            super(\"ExampleModule\", \"Пример модуля из аддона\", ModuleCategory.MISC);\n" +
        "        }\n" +
        "    }\n" +
        "}\n"
}
