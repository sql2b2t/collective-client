package me.collective.addon

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.HashSet
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

/**
 * Сборка исходника аддона прямо во время работы клиента через JavaCompiler.
 * Полноценно работает в dev-окружении (там в classpath лежат классы Minecraft);
 * в обычном клиенте компилятор вернёт понятную ошибку, а исходник останется в collective/addons-src
 * для ручной сборки.
 */
object AddonCompiler {
    data class BuildResult(val success: Boolean, val message: String, val jar: Path?)

    /** Компилирует исходник, пакует классы в jar в collective/addons и пытается загрузить аддон. */
    fun build(sourceFile: Path?, addonName: String?): BuildResult {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            return BuildResult(false, "Исходник не найден: $sourceFile", null)
        }

        var safeName = AddonManager.sanitizeJavaName(addonName)
        if (safeName == null) {
            return BuildResult(false, "Недопустимое имя аддона: $addonName", null)
        }
        safeName = safeName.substring(0, safeName.length - ".java".length)

        val compiler = ToolProvider.getSystemJavaCompiler()
        if (compiler == null) {
            return BuildResult(
                false,
                "JavaCompiler недоступен: клиент запущен на JRE, а не на JDK. " +
                    "Соберите аддон вручную или запустите клиент на JDK.",
                null
            )
        }

        val buildRoot = AddonManager.getInstance().addonsSrcFolder.resolve("build").resolve(safeName)
        val classesDir = buildRoot.resolve("classes")
        try {
            deleteRecursively(buildRoot)
            Files.createDirectories(classesDir)
        } catch (e: Exception) {
            return BuildResult(false, "Не удалось подготовить папку сборки: ${e.message}", null)
        }

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val ok: Boolean
        try {
            val fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
            try {
                val units = fileManager.getJavaFileObjects(sourceFile.toFile())
                val options = listOf(
                    "-classpath", buildClasspath(),
                    "-d", classesDir.toString(),
                    "-proc:none",
                    "-nowarn"
                )
                ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
            } finally {
                fileManager.close()
            }
        } catch (t: Throwable) {
            return BuildResult(false, "Сбой компилятора: $t", null)
        }

        if (!ok) {
            val sb = StringBuilder("Ошибки компиляции:")
            var shown = 0
            for (d in diagnostics.diagnostics) {
                if (d.kind != Diagnostic.Kind.ERROR) continue
                val line = d.lineNumber
                val msg = d.getMessage(java.util.Locale.ROOT)
                sb.append("\n - ").append(if (line > 0) "строка $line: " else "").append(msg)
                if (++shown >= 6) break
            }
            if (shown == 0) sb.append(" неизвестная ошибка")
            sb.append(
                "\nПодсказка: мгновенная сборка работает при dev-запуске клиента (./gradlew runClient); " +
                    "иначе соберите аддон вручную по исходнику из collective/addons-src."
            )
            return BuildResult(false, sb.toString(), null)
        }

        val jarPath = AddonManager.getInstance().addonsFolder.resolve("$safeName.jar")
        try {
            createJar(classesDir, jarPath)
        } catch (e: Exception) {
            return BuildResult(false, "Не удалось собрать jar: ${e.message}", null)
        }

        val loaded = AddonManager.getInstance().loadJarAddon(jarPath.toFile())
        if (loaded) {
            return BuildResult(true, "Аддон собран и загружен: ${jarPath.fileName}", jarPath)
        }
        return BuildResult(false, "Jar собран (${jarPath.fileName}), но не загрузился — смотрите .addon list", jarPath)
    }

    /** Classpath: системный + URL'ы всех загрузчиков (в dev-окружении там же лежат классы Minecraft). */
    private fun buildClasspath(): String {
        val cp = StringBuilder()
        val system = System.getProperty("java.class.path")
        if (!system.isNullOrEmpty()) cp.append(system)

        val seen: MutableSet<String> = HashSet()
        var cl: ClassLoader? = AddonCompiler::class.java.classLoader
        while (cl != null) {
            if (cl is URLClassLoader) {
                for (url: URL in cl.urLs) {
                    try {
                        val path = Paths.get(url.toURI()).toString()
                        if (seen.add(path)) {
                            if (cp.isNotEmpty()) cp.append(File.pathSeparator)
                            cp.append(path)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            cl = cl.parent
        }
        return cp.toString()
    }

    private fun createJar(classesDir: Path, jarPath: Path) {
        JarOutputStream(Files.newOutputStream(jarPath)).use { jos ->
            Files.walk(classesDir).use { stream ->
                val files = stream.filter(Files::isRegularFile).sorted().toList()
                for (file in files) {
                    val entryName = classesDir.relativize(file).toString().replace('\\', '/')
                    jos.putNextEntry(JarEntry(entryName))
                    Files.copy(file, jos)
                    jos.closeEntry()
                }
            }
        }
    }

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            val paths = stream.sorted(Comparator.reverseOrder()).toList()
            for (p in paths) {
                Files.deleteIfExists(p)
            }
        }
    }
}
