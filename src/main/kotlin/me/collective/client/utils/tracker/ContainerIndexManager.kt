package me.collective.client.utils.tracker

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.io.File

/**
 * Общий доступ к индексу контейнеров.
 *
 * <p>Индекс один на процесс, а не на модуль: его читают и тултипы, и
 * сортировщик. Держим счётчик активных пользователей — когда он упал
 * до нуля, индекс сбрасывается, чтобы не держать память и файл открытыми.
 */
class ContainerIndexManager private constructor() {
    companion object {
        private const val AUTOSAVE_INTERVAL_MS = 60000L

        private var sharedData: ContainerIndex? = null
        private var activeModuleCount = 0
        private var currentServerIdentifier: String? = null
        private var lastAutosave = System.currentTimeMillis()

        @JvmStatic
        @Synchronized
        fun onModuleActivate(): ContainerIndex? {
            activeModuleCount++
            return sharedData
        }

        /** Сколько модулей сейчас пользуются индексом — иначе индекс зря висит в памяти. */
        @JvmStatic
        @Synchronized
        fun activeCount(): Int {
            return activeModuleCount
        }

        @JvmStatic
        @Synchronized
        fun onModuleDeactivate() {
            activeModuleCount--
            if (activeModuleCount <= 0) {
                sharedData?.saveData()
                sharedData = null
                activeModuleCount = 0
                currentServerIdentifier = null
            }
        }

        @JvmStatic
        @Synchronized
        fun getData(): ContainerIndex {
            val serverNow = currentServerIdentifier()
            val shared = sharedData
            val identifier = currentServerIdentifier
            if (shared != null && identifier != null && identifier != serverNow) {
                // Сменился сервер — старый индекс сохраняем и перечитываем
                shared.saveData()
                shared.reinitializeForNewServer()
                shared.loadData()
                currentServerIdentifier = serverNow
            }

            if (sharedData == null) {
                sharedData = ContainerIndex()
                sharedData!!.loadData()
                currentServerIdentifier = serverNow
            }

            return sharedData!!
        }

        @JvmStatic
        @Synchronized
        fun saveData() {
            sharedData?.saveData()
        }

        /** Автосохранение раз в минуту, чтобы индекс не потерялся при краше. */
        @JvmStatic
        @Synchronized
        fun tickAutosave() {
            val shared = sharedData ?: return
            if (!shared.isDirty()) return
            val now = System.currentTimeMillis()
            if (now - lastAutosave >= AUTOSAVE_INTERVAL_MS) {
                lastAutosave = now
                shared.saveData()
            }
        }

        /** Вызывается из общего обработчика входа в мир. */
        @JvmStatic
        @Synchronized
        fun onWorldJoin() {
            val serverNow = currentServerIdentifier()
            val shared = sharedData
            if (shared != null) {
                shared.saveData()
                shared.reinitializeForNewServer()
                shared.loadData()
                currentServerIdentifier = serverNow
            } else if (activeModuleCount > 0) {
                sharedData = ContainerIndex()
                sharedData!!.loadData()
                currentServerIdentifier = serverNow
            }
        }

        private fun currentServerIdentifier(): String {
            val mc = Minecraft.getInstance()
            if (mc == null) return "unknown"
            val currentServer = mc.currentServer
            if (currentServer != null) return currentServer.ip
            if (mc.isLocalServer && mc.singleplayerServer != null) {
                val singleplayerServer = mc.singleplayerServer ?: return "unknown"
                return "singleplayer_" + singleplayerServer.worldData.levelName
            }
            return "unknown"
        }

        /** Куда класть экспорт — рядом с остальными файлами индекса. */
        @JvmStatic
        fun exportTarget(filename: String): File {
            return FabricLoader.getInstance().gameDir.resolve("collective/ChestTracker").resolve(filename).toFile()
        }
    }
}
