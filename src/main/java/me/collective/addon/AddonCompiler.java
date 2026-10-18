package me.collective.addon;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Сборка исходника аддона прямо во время работы клиента через JavaCompiler.
 * Полноценно работает в dev-окружении (там в classpath лежат yarn-классы Minecraft);
 * в обычном клиенте компилятор вернёт понятную ошибку, а исходник останется в collective/addons-src
 * для ручной сборки.
 */
public final class AddonCompiler {

    public record BuildResult(boolean success, String message, Path jar) {}

    private AddonCompiler() {}

    /** Компилирует исходник, пакует классы в jar в collective/addons и пытается загрузить аддон. */
    public static BuildResult build(Path sourceFile, String addonName) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            return new BuildResult(false, "Исходник не найден: " + sourceFile, null);
        }

        String safeName = AddonManager.sanitizeJavaName(addonName);
        if (safeName == null) {
            return new BuildResult(false, "Недопустимое имя аддона: " + addonName, null);
        }
        safeName = safeName.substring(0, safeName.length() - ".java".length());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new BuildResult(false, "JavaCompiler недоступен: клиент запущен на JRE, а не на JDK. "
                    + "Соберите аддон вручную или запустите клиент на JDK.", null);
        }

        Path buildRoot = AddonManager.getInstance().getAddonsSrcFolder().resolve("build").resolve(safeName);
        Path classesDir = buildRoot.resolve("classes");
        try {
            deleteRecursively(buildRoot);
            Files.createDirectories(classesDir);
        } catch (IOException e) {
            return new BuildResult(false, "Не удалось подготовить папку сборки: " + e.getMessage(), null);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile.toFile());
            List<String> options = List.of(
                    "-classpath", buildClasspath(),
                    "-d", classesDir.toString(),
                    "-proc:none",
                    "-nowarn");
            ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
        } catch (Throwable t) {
            return new BuildResult(false, "Сбой компилятора: " + t, null);
        }

        if (!ok) {
            StringBuilder sb = new StringBuilder("Ошибки компиляции:");
            int shown = 0;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                long line = d.getLineNumber();
                String msg = d.getMessage(Locale.ROOT);
                sb.append("\n - ").append(line > 0 ? "строка " + line + ": " : "").append(msg);
                if (++shown >= 6) break;
            }
            if (shown == 0) sb.append(" неизвестная ошибка");
            sb.append("\nПодсказка: мгновенная сборка работает при dev-запуске клиента (./gradlew runClient); "
                    + "иначе соберите аддон вручную по исходнику из collective/addons-src.");
            return new BuildResult(false, sb.toString(), null);
        }

        Path jarPath = AddonManager.getInstance().getAddonsFolder().resolve(safeName + ".jar");
        try {
            createJar(classesDir, jarPath);
        } catch (IOException e) {
            return new BuildResult(false, "Не удалось собрать jar: " + e.getMessage(), null);
        }

        boolean loaded = AddonManager.getInstance().loadJarAddon(jarPath.toFile());
        if (loaded) {
            return new BuildResult(true, "Аддон собран и загружен: " + jarPath.getFileName(), jarPath);
        }
        return new BuildResult(false, "Jar собран (" + jarPath.getFileName() + "), но не загрузился — смотрите .addon list", jarPath);
    }

    /** Classpath: системный + URL'ы всех загрузчиков (в dev-окружении там же лежат yarn-классы Minecraft). */
    private static String buildClasspath() {
        StringBuilder cp = new StringBuilder();
        String system = System.getProperty("java.class.path");
        if (system != null && !system.isEmpty()) cp.append(system);

        Set<String> seen = new HashSet<>();
        ClassLoader cl = AddonCompiler.class.getClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL url : ucl.getURLs()) {
                    try {
                        String path = Paths.get(url.toURI()).toString();
                        if (seen.add(path)) {
                            if (cp.length() > 0) cp.append(File.pathSeparator);
                            cp.append(path);
                        }
                    } catch (Exception ignored) {}
                }
            }
            cl = cl.getParent();
        }
        return cp.toString();
    }

    private static void createJar(Path classesDir, Path jarPath) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath));
             Stream<Path> stream = Files.walk(classesDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String entryName = classesDir.relativize(file).toString().replace('\\', '/');
                jos.putNextEntry(new JarEntry(entryName));
                Files.copy(file, jos);
                jos.closeEntry();
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}
