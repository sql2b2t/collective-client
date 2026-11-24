# Миграция Collective Client на Kotlin (половина кода)

Цель: ~70k строк из ~142k ядра (`src/main/java/me/collective/client`) на Kotlin.
Прогресс фиксируется здесь — это единственный источник состояния между сессиями.

## Инфраструктура (СДЕЛАНО)
- `build.gradle`: плагин `org.jetbrains.kotlin.jvm` 2.4.21, jvmTarget 21, `-Xjvm-default=all`.
- FLK `net.fabricmc:fabric-language-kotlin:1.14.2+kotlin.2.4.21` — modImplementation + include (версия согласована с уже вшитым kotlin 2.4.21).
- `fabric.mod.json`: depends `fabric-language-kotlin`.
- Kotlin-исходники: `src/main/kotlin/` (пакеты зеркалят java). Loom ремапит Kotlin автоматически.
- Кросс-компиляция Java↔Kotlin проверена (EXIT 0).

## Конвенции конвертации
1. **Не конвертировать**: `mixin/` (вне client), `bootstrap/`, `ClientContext.java`, `managers/`, `render/` (шейдерный движок, MeshBuffer-статик). `modules/combat` конвертируется (прежнее исключение отменено) — с осторожностью: античит-тайминги, статические кэши читаемые миксинами → `@JvmField` в companion, grep использований в mixin/ перед конвертацией.
2. `@EventHandler`-методы: оставить `private fun` — EventBus через LambdaMetafactory работает с Kotlin-методами.
3. Поля, читаемые через рефлексию/Gson/биндинги (`ModuleDependencies.bind*`, статические кэши в модулях) → `@JvmField` или companion `@JvmStatic`.
4. Порядок инициализации `ClientInitializer` не менять — он в Java и не трогается.
5. Строки настроек (`configName`), имена модулей — не менять (совместимость конфигов).
6. Конвертация: Java-файл удаляется, .kt ложится в тот же пакет в `src/main/kotlin`.
7. После каждого батча — `./gradlew compileKotlin compileJava` и только потом следующий батч.

## План батчей (порядок)
- [x] Б1: `settings/options` — простые enum'ы (23 шт, скрипт-трансформер)
- [x] Б2: `settings/options` — 37 усложнённых enum'ов конвертированы (SimpleMacro/QueuedMacro/ComparatorInventorySortStrategy — классы, не enum'ы; @JvmStatic для Java-статик-вызовов; compile EXIT 0).
- [x] Б3: `events/` (83 файла) — event-классы и шина событий полностью в Kotlin (compileKotlin: 0 ошибок в events/).
- [x] Б4: `media/` + `audio/` + `config/` + `integrations/` (~2.5k) — полностью в Kotlin, 0 ошибок.
- [x] Б5: `utils/` листовые пакеты по одному: chat, collections, math, combat, entity, inventory, movement, network, world (15.5k) — полностью в Kotlin, 0 ошибок.
- [x] Б6: `modules/client` + `modules/hud` — полностью в Kotlin, 0 ошибок.
- [x] Б7: `modules/misc` + `modules/player` — полностью в Kotlin, 0 ошибок.
- [x] Б8: `modules/movement` + `modules/render` + `commands/` — полностью в Kotlin, 0 ошибок.
- [x] Б9: `modules/combat/` + `modules/exploit/` — полностью в Kotlin (в `modules/` 0 `.java`)
- [x] Б10: `client/access/BotConnectionAccess` + `bots/network/AbstractBotPlayPacketListener` (137 override) + `addon/` (AddonInfo/CollectiveAddon/AddonCompiler/AddonManager) + `scripting/` (ScriptContext/ScriptCustomCommand/ScriptHudElement/ScriptModule/ScriptManager) — полностью в Kotlin, BUILD SUCCESSFUL
- Цель «половины» достигнута (~76k строк на Kotlin); компиляция `./gradlew compileKotlin compileJava` завершается с **BUILD SUCCESSFUL (0 ошибок)**.

## Инструменты автоматизации
- `scripts/kotlin_migrator_gui.py` — полноценная программа с графическим интерфейсом в стиле **Material Design 3 (Material You)**:
  - Автоматическое обнаружение неконвертированных модулей и расчет LOC/прогресса.
  - One-Click автоматическая конвертация (все модули, батчами по категориям или пофайлово).
  - Встроенный live-терминал с логами и кнопками верификации Gradle.
  - Запуск: `python3 scripts/kotlin_migrator_gui.py` или `python3 scripts/kotlin_migrator.py gui` (открывается по адресу `http://localhost:8088`).
- `scripts/module_scanner.py` — сканер структуры проекта (поиск оставшихся `.java`, фильтрация исключений `mixin/`, `bootstrap/`, подсчет строк кода).
- `scripts/kotlin_migrator.py` — консольный мастер-пайплайн:
  - `convert <path>`: автоматическая конвертация синтаксиса `.java` в `.kt` с удалением исходного файла и запуском фиксера.
  - `fix [path]`: исправление распространенных ошибок миграции (null-safety, ключевые слова, вызовы методов, висячие операторы).
  - `verify`: запуск полной проверки `./gradlew compileKotlin compileJava`.
  - `all <path>`: полный цикл (convert + fix + verify) в одну команду.
  - `gui`: запуск интерфейса Material Design 3.

## Счётчик
- Kotlin: 948 файлов (~128 000 строк); сканер client-ядра: 939 файлов / 127 943 строк, 100%.
- Завершённые пакеты (0 ошибок): `settings/options`, `events/` (вкл. bus), `media/`, `audio/`, `config/`, `integrations/`, `utils/` (все 9 пакетов), `commands/`, `modules/*`, `bots/`, `access/`, `addon/`, `scripting/`.
- Осталась только Java: 358 файлов — исключённые по конвенциям пакеты `mixin/` (221 файл, ~12.7k строк), `render/` (92 файла, ~8.7k строк), `managers/` (39 файлов, ~6.7k строк), `bootstrap/` + корневые `ClientContext`/`ClientAccess`/`CollectiveClient`/`NamedFeature`. Если идти дальше плана: Б11 `managers/` (осторожно: Gson/рефлексия, `@JvmField`), Б12 `render/` (шейдерный движок, статики). `mixin/` не конвертировать — ломается Fabric Mixin tooling.
- Текущий статус компиляции: **`./gradlew compileKotlin compileJava assemble` -> BUILD SUCCESSFUL (0 ошибок)**. Сформирован рабочий jar: `build/libs/collective-1.0.3.jar`.
