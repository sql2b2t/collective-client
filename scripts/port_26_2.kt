// port_26_2.kt — автоматическое портирование Collective Client на Minecraft 26.2.
//
// Что делает:
//   1. копирует проект в целевую папку (build/, .gradle/, run/, .kotlin/ исключаются)
//   2. правит build-слой: fabric-loom вместо fabric-loom-remap, Java 25, конфигурации mod* -> обычные
//   3. правит gradle.properties, fabric.mod.json (depends minecraft/java)
//   4. применяет таблицу ренеймов (классы + методы) ко всем исходникам:
//      FQN, дескрипторы L...; , внутренние имена net/minecraft/... (accesswidener), простые имена
//   5. ищет intermediary-имена (method_/field_/class_) и пишет отчёт PORT_26_2.md
//
// Таблицу ренеймов 1.21.11 -> 26.2 сгенерируй через MC-Mapping-Reference
// (github.com/HelixCraft/MC-Mapping-Reference, generate_changelog_v2.py) и подай через --renames:
//   TSV: <old> <TAB> <new> <TAB> CLASS|METHOD   (третья колонка опциональна)
//   ./port_26_2 --renames changelog-mojmap.tsv
//
// Сборка/запуск без kotlinc (используется компилятор из кеша Gradle), см. PORT_26_2.md.
//
// Запуск: java -cp <out>:<kotlin-stdlib> Port262Kt [--source SRC] [--target DST] [--force] [--renames FILE]

@file:JvmName("Port262Main")

import java.io.File

enum class Kind { CLASS, METHOD }

class Rename(val old: String, val new: String, val kind: Kind) {
    var hits: Int = 0
}

// ---------- константы порта ----------
const val FROM_MC = "1.21.11"
const val TO_MC = "26.2"
const val FROM_JAVA = 21
const val TO_JAVA = 25

// каталоги, которые не тащим в новый проект
val EXCLUDED_DIRS = setOf("build", ".gradle", "run", ".kotlin", ".git", ".idea", "out")

// какие файлы прогоняем через таблицу ренеймов
val RENAME_EXTS = setOf("java", "kt", "kts", "accesswidener", "json")

// ---------- seed-таблица (минимум по данным портингов 26.x; расширяй --renames) ----------
fun seedRenames(): List<Rename> = listOf(
    // Fabric API 26.x: переименования из porting-гайда
    Rename("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper",
           "net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper", Kind.CLASS),
    Rename("KeyBindingHelper", "KeyMappingHelper", Kind.CLASS),
    Rename("net.fabricmc.fabric.api.networking.v1.PacketByteBufs",
           "net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs", Kind.CLASS),
    Rename("PacketByteBufs", "FriendlyByteBufs", Kind.CLASS),
    // vanilla mojmap 1.21.x -> 26.x (пример из changelog маппингов)
    Rename("net.minecraft.world.entity.MobSpawnType",
           "net.minecraft.world.entity.EntitySpawnReason", Kind.CLASS),
    Rename("MobSpawnType", "EntitySpawnReason", Kind.CLASS),
)

// ---------- утилиты ----------
fun countMatches(text: String, pattern: Regex): Int = pattern.findAll(text).count()

fun replaceCounted(text: String, pattern: Regex, replacement: String): Pair<String, Int> {
    var n = 0
    val out = pattern.replace(text) { _ -> n++; replacement }
    return out to n
}

// границы, чтобы не задеть длинные идентификаторы (net.minecraft.X vs net.minecraft.XY)
fun fqnPattern(fqn: String) =
    Regex("(?<![A-Za-z0-9_.])" + Regex.escape(fqn) + "(?![A-Za-z0-9_.])")

fun slashedPattern(slashed: String) =
    Regex("(?<![A-Za-z0-9_/])" + Regex.escape(slashed) + "(?![A-Za-z0-9_/])")

fun descriptorPattern(slashed: String) = Regex("L" + Regex.escape(slashed) + ";")

fun wordPattern(simple: String) = Regex("\\b" + Regex.escape(simple) + "\\b")

fun callPattern(name: String) = Regex("\\b" + Regex.escape(name) + "(?=\\s*\\()")

fun loadRenames(file: File): List<Rename> {
    val out = mutableListOf<Rename>()
    file.readLines().forEachIndexed { idx, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        val cols = line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
        if (cols.size < 2) {
            System.err.println("[warn] ${file.name}:${idx + 1}: ожидалось 2-3 колонки TSV, пропущено")
            return@forEachIndexed
        }
        val kind = when (cols.getOrNull(2)?.uppercase()) {
            "METHOD" -> Kind.METHOD
            "CLASS", null, "" -> Kind.CLASS
            else -> { System.err.println("[warn] ${file.name}:${idx + 1}: kind=${cols[2]}, пропущено"); return@forEachIndexed }
        }
        out.add(Rename(cols[0], cols[1], kind))
    }
    return out
}

// ---------- копирование ----------
fun copyTree(src: File, dst: File, copied: MutableList<File>) {
    src.listFiles()?.sortedBy { it.name }?.forEach { child ->
        if (child.isDirectory) {
            if (child.name in EXCLUDED_DIRS) return@forEach
            val targetDir = File(dst, child.name)
            targetDir.mkdirs()
            copyTree(child, targetDir, copied)
        } else {
            child.copyTo(File(dst, child.name), overwrite = true)
            copied.add(child)
        }
    }
}

// ---------- специализированные правки ----------
fun transformGradleProperties(text: String): String {
    val sb = StringBuilder()
    text.lines().forEach { line ->
        when {
            line.startsWith("minecraft_version=") -> sb.append("minecraft_version=$TO_MC").append('\n')
            line.startsWith("fabric_api_version=") -> {
                sb.append("# TODO(26.2): взять актуальную версию с https://fabricmc.net/develop").append('\n')
                sb.append(line).append('\n')
            }
            line.startsWith("loader_version=") -> {
                sb.append("# TODO(26.2): проверить версию loader для 26.2 на https://fabricmc.net/develop").append('\n')
                sb.append(line).append('\n')
            }
            line.startsWith("loom_version=") -> {
                sb.append("# TODO(26.2): версия fabric-loom для 26.2, плагин заменён на fabric-loom").append('\n')
                sb.append(line).append('\n')
            }
            else -> sb.append(line).append('\n')
        }
    }
    return sb.toString()
}

fun transformBuildGradle(text: String): String {
    var t = text
    // плагин: loom-remap -> fabric-loom (в 26.x loom ремапит по умолчанию)
    t = t.replace("net.fabricmc.fabric-loom-remap", "net.fabricmc.fabric-loom")
    // mappings-строка больше не нужна (mojmap дефолт)
    t = t.replace(Regex("(?m)^\\s*mappings loom\\.officialMojangMappings\\(\\)\\s*$"),
        "// TODO(26.2): строка mappings удалена, mojmap дефолт в fabric-loom")
    // mod* конфигурации -> обычные
    t = wordPattern("modImplementation").replace(t, "implementation")
    t = wordPattern("modCompileOnly").replace(t, "compileOnly")
    t = wordPattern("modLocalRuntime").replace(t, "localRuntime")
    t = wordPattern("modApi").replace(t, "api")
    // remapJar -> jar
    t = t.replace("tasks.named('remapJar')", "tasks.named('jar')")
    // Java 21 -> 25
    t = t.replace("JavaLanguageVersion.of($FROM_JAVA)", "JavaLanguageVersion.of($TO_JAVA)")
    t = t.replace("options.release = $FROM_JAVA", "options.release = $TO_JAVA")
    val banner = """
        |// ============================================================
        |// TODO(26.2) — заполнить перед первой сборкой:
        |//  - fabric_api_version, loader_version, loom_version: https://fabricmc.net/develop
        |//  - fabric-language-kotlin: версия под 26.2 (согласована с kotlin 2.4.21)
        |//  - viafabricplus-api (vfp-maven/): перегенерировать из релиза под 26.2
        |//  - baritone-meteor: сборка под 26.2 (сейчас 1.21.11 — будет падать линк)
        |//  - modCompileOnly моды (sodium/lithium/xaero/immediatelyfast): новые versionId с Modrinth
        |// ============================================================
    """.trimMargin()
    return banner + "\n" + t
}

fun transformFabricModJson(text: String): String {
    var t = text
    t = Regex("\"minecraft\"\\s*:\\s*\"[^\"]*\"").replace(t, "\"minecraft\": \">=$TO_MC\"")
    t = Regex("\"java\"\\s*:\\s*\">=[0-9]+\"").replace(t, "\"java\": \">=$TO_JAVA\"")
    t = t.replace(FROM_MC, TO_MC) // описание и suggests
    return t
}

// ---------- основной проход ренеймов ----------
data class RewriteStats(val filesTouched: Int, val totalHits: Int)

fun applyRenames(root: File, renames: List<Rename>): RewriteStats {
    val classRenames = renames.filter { it.kind == Kind.CLASS }.sortedByDescending { it.old.length }
    val methodRenames = renames.filter { it.kind == Kind.METHOD }.sortedByDescending { it.old.length }
    var filesTouched = 0
    var totalHits = 0

    root.walkBottomUp().filter { it.isFile }.forEach { f ->
        val ext = f.extension.lowercase()
        if (ext !in RENAME_EXTS) return@forEach
        // сам портер не ренеймим (иначе seed-таблица в его копии перепишется)
        if (f.name == "port_26_2.kt") return@forEach

        var text = f.readText()
        val before = text

        for (r in classRenames) {
            if (r.old !in text) continue
            val simpleOld = r.old.substringAfterLast('.')
            val simpleNew = r.new.substringAfterLast('.')
            var n = 0

            // 1) dotted FQN: import net.minecraft...; и квалифицированные ссылки
            if (r.old.contains('.')) {
                val (t2, c) = replaceCounted(text, fqnPattern(r.old), r.new); text = t2; n += c
                // 2) внутренние имена: accesswidener и т.п. — net/minecraft/...
                val (t3, c3) = replaceCounted(text, slashedPattern(r.old.replace('.', '/')), r.new.replace('.', '/'))
                text = t3; n += c3
                // 3) дескрипторы: Lnet/minecraft/...; (mixin-строки, сигнатуры)
                val (t4, c4) = replaceCounted(text, descriptorPattern(r.old.replace('.', '/')), "L" + r.new.replace('.', '/') + ";")
                text = t4; n += c4
            }

            // 4) простое имя (после FQN-прохода остатки: unqualified-использования)
            if (simpleOld != simpleNew) {
                val (t5, c5) = replaceCounted(text, wordPattern(simpleOld), simpleNew)
                text = t5; n += c5
            }
            r.hits += n
        }

        for (r in methodRenames) {
            if (r.old !in text) continue
            val (t2, c) = replaceCounted(text, callPattern(r.old), r.new)
            text = t2; r.hits += c
        }

        if (text != before) {
            f.writeText(text)
            filesTouched++
        }
    }
    val realTotal = renames.sumOf { it.hits }
    return RewriteStats(filesTouched, realTotal)
}

// ---------- скан intermediary-имён ----------
fun scanIntermediary(root: File): Map<File, Int> {
    val pattern = Regex("\\b(?:method|field|class)_[0-9]+\\b")
    val out = mutableMapOf<File, Int>()
    root.walkBottomUp().filter { it.isFile && it.extension.lowercase() in setOf("java", "kt") }
        .forEach { f ->
            val c = countMatches(f.readText(), pattern)
            if (c > 0) out[f] = c
        }
    return out
}

// ---------- отчёт ----------
fun writeReport(target: File, copied: Int, stats: RewriteStats, renames: List<Rename>,
                intermediary: Map<File, Int>, renamesFrom: List<String>) {
    val md = StringBuilder()
    md.append("# Порт Collective Client на Minecraft $TO_MC\n\n")
    md.append("Сгенерировано `scripts/port_26_2.kt`. Механика выполнена, остальное — по чеклисту.\n\n")
    md.append("## Что сделано\n")
    md.append("- скопировано файлов: **$copied** (без build/, .gradle/, run/, .kotlin/)\n")
    md.append("- build.gradle: fabric-loom, Java $TO_JAVA, mod* конфигурации заменены, remapJar -> jar\n")
    md.append("- gradle.properties: minecraft_version=$TO_MC\n")
    md.append("- fabric.mod.json: depends minecraft >=$TO_MC, java >=$TO_JAVA\n")
    md.append("- файлов с применёнными ренеймами: **${stats.filesTouched}**, замен всего: **${stats.totalHits}**\n")
    if (renamesFrom.isNotEmpty()) md.append("- таблицы ренеймов из: ${renamesFrom.joinToString(", ")}\n\n")

    md.append("## Таблица ренеймов и попадания\n\n")
    md.append("| kind | old | new | замен |\n|---|---|---|---|\n")
    renames.forEach { r ->
        md.append("| ${r.kind} | `${r.old}` | `${r.new}` | ${r.hits} |\n")
    }
    md.append("\n")

    md.append("## TODO перед первой сборкой\n\n")
    md.append("1. [ ] `gradle.properties`: fabric_api_version / loader_version / loom_version с https://fabricmc.net/develop\n")
    md.append("2. [ ] `build.gradle`: fabric-language-kotlin под 26.2, kotlin 2.4.21 согласованность\n")
    md.append("3. [ ] `vfp-maven/`: viafabricplus-api перегенерировать из релиза 26.2 (см. комментарий в build.gradle)\n")
    md.append("4. [ ] baritone-meteor: сборка под $TO_MC (pinned sha256 в build.gradle тоже поменять)\n")
    md.append("5. [ ] modCompileOnly моды (sodium/lithium/xaero/immediatelyfast/entityculling): новые versionId с Modrinth\n")
    md.append("6. [ ] добить таблицу ренеймов из MC-Mapping-Reference (mojmap 1.21.11 -> $TO_MC) и прогнать ещё раз\n")
    md.append("7. [ ] render/ движок: в 26.2 переломаны rendering entry points — почти наверняка rewrite\n\n")

    md.append("## Intermediary-имена (остались method_/field_/class_ — править руками)\n\n")
    if (intermediary.isEmpty()) {
        md.append("не найдены\n")
    } else {
        intermediary.forEach { (f, c) ->
            md.append("- $f — $c вхождений\n")
        }
    }
    md.append("\n## Цикл проверки\n\n```\n./gradlew compileKotlin compileJava\n./gradlew runClient -Dmixin.debug.export=true\n```\n")
    md.append("\nМиксины падают в рантайме, не в компиляции: `defaultRequire: 1` в collective.mixins.json,\n")
    md.append("предупреждения annotation processor \"Cannot find target\" считать ошибками.\n")

    File(target, "PORT_26_2.md").writeText(md.toString())
}

// ---------- main ----------
fun main(args: Array<String>) {
    var source = File("/home/anvres/Projects/collective-client")
    var target = File("/home/anvres/Projects/collective-client-26.2")
    var force = false
    val renameFiles = mutableListOf<File>()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--source" -> { source = File(args[i + 1]); i += 2 }
            "--target" -> { target = File(args[i + 1]); i += 2 }
            "--force"  -> { force = true; i += 1 }
            "--renames" -> { renameFiles.add(File(args[i + 1])); i += 2 }
            "--help", "-h" -> {
                println("port_26_2 [--source SRC] [--target DST] [--force] [--renames TSV...]")
                return
            }
            else -> { System.err.println("неизвестный аргумент: ${args[i]} (--help)"); return }
        }
    }

    if (!File(source, "build.gradle").isFile || !File(source, "settings.gradle").isFile) {
        System.err.println("в ${source.canonicalPath} нет build.gradle/settings.gradle — это не проект?")
        return
    }
    if (target.exists() && target.listFiles()?.isNotEmpty() == true && !force) {
        System.err.println("цель ${target.canonicalPath} не пуста — сотрёт её только --force")
        return
    }
    target.mkdirs()

    val renames = seedRenames().toMutableList()
    val renamesFrom = mutableListOf<String>()
    renameFiles.forEach { f ->
        if (!f.isFile) { System.err.println("не найден файл ренеймов: $f"); return }
        renames.addAll(loadRenames(f))
        renamesFrom.add(f.path)
    }

    println("[1/5] копирую ${source.canonicalPath} -> ${target.canonicalPath}")
    val copied = mutableListOf<File>()
    copyTree(source, target, copied)
    println("      файлов: ${copied.size}")

    println("[2/5] правлю build-слой")
    val gp = File(target, "gradle.properties")
    if (gp.isFile) gp.writeText(transformGradleProperties(gp.readText()))
    else println("      gradle.properties не найден — пропущено")
    val bg = File(target, "build.gradle")
    if (bg.isFile) bg.writeText(transformBuildGradle(bg.readText()))
    else println("      build.gradle не найден — пропущено")
    val fmj = File(target, "src/main/resources/fabric.mod.json")
    if (fmj.isFile) fmj.writeText(transformFabricModJson(fmj.readText()))

    println("[3/5] применяю таблицу ренеймов (${renames.size} записей)")
    val stats = applyRenames(target, renames)
    println("      файлов изменено: ${stats.filesTouched}, замен: ${stats.totalHits}")

    println("[4/5] сканирую intermediary-имена")
    val intermediary = scanIntermediary(target)
    println("      файлов с method_/field_/class_: ${intermediary.size}")

    println("[5/5] пишу отчёт PORT_26_2.md")
    writeReport(target, copied.size, stats, renames, intermediary, renamesFrom)

    println("\nготово: ${target.canonicalPath}")
    println("дальше — по чеклисту в PORT_26_2.md, потом ./gradlew compileKotlin compileJava")
}
