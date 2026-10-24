package me.collective.util.tracker;

import net.minecraft.client.Minecraft;

import java.io.File;

/**
 * Общий доступ к индексу контейнеров.
 *
 * <p>Индекс один на процесс, а не на модуль: его читают и тултипы, и
 * сортировщик. Держим счётчик активных пользователей — когда он упал
 * до нуля, индекс сбрасывается, чтобы не держать память и файл открытыми.
 */
public class ChestTrackerDataManager {
    private static final long AUTOSAVE_INTERVAL_MS = 60000L;

    private static ChestTrackerDataV2 sharedData = null;
    private static int activeModuleCount = 0;
    private static String currentServerIdentifier = null;
    private static long lastAutosave = System.currentTimeMillis();

    private ChestTrackerDataManager() {
    }

    public static synchronized ChestTrackerDataV2 onModuleActivate() {
        activeModuleCount++;
        return sharedData;
    }

    /** Сколько модулей сейчас пользуются индексом — иначе индекс зря висит в памяти. */
    public static synchronized int activeCount() {
        return activeModuleCount;
    }

    public static synchronized void onModuleDeactivate() {
        activeModuleCount--;
        if (activeModuleCount <= 0) {
            if (sharedData != null) {
                sharedData.saveData();
                sharedData = null;
            }
            activeModuleCount = 0;
            currentServerIdentifier = null;
        }
    }

    public static synchronized ChestTrackerDataV2 getData() {
        String serverNow = currentServerIdentifier();
        if (sharedData != null && currentServerIdentifier != null && !currentServerIdentifier.equals(serverNow)) {
            // Сменился сервер — старый индекс сохраняем и перечитываем
            sharedData.saveData();
            sharedData.reinitializeForNewServer();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        }

        if (sharedData == null) {
            sharedData = new ChestTrackerDataV2();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        }

        return sharedData;
    }

    public static synchronized void saveData() {
        if (sharedData != null) sharedData.saveData();
    }

    /** Автосохранение раз в минуту, чтобы индекс не потерялся при краше. */
    public static synchronized void tickAutosave() {
        if (sharedData == null || !sharedData.isDirty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAutosave >= AUTOSAVE_INTERVAL_MS) {
            lastAutosave = now;
            sharedData.saveData();
        }
    }

    /** Вызывается из общего обработчика входа в мир. */
    public static synchronized void onWorldJoin() {
        String serverNow = currentServerIdentifier();
        if (sharedData != null) {
            sharedData.saveData();
            sharedData.reinitializeForNewServer();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        } else if (activeModuleCount > 0) {
            sharedData = new ChestTrackerDataV2();
            sharedData.loadData();
            currentServerIdentifier = serverNow;
        }
    }

    private static String currentServerIdentifier() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "unknown";
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null
            ? "singleplayer_" + mc.getSingleplayerServer().getWorldData().getLevelName()
            : "unknown";
    }

    /** Куда класть экспорт — рядом с остальными файлами индекса. */
    static File exportTarget(String filename) {
        return new File(new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "collective/ChestTracker"), filename);
    }
}