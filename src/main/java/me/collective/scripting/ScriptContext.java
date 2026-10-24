package me.collective.scripting;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.command.CommandManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.hud.HudModule;
import me.collective.util.BaritoneBridge;
import me.collective.util.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptContext {
    /** Сколько ошибок допускается от одного источника (модуль/таймер), прежде чем он будет остановлен. */
    public static final int MAX_ERRORS = 5;

    public final Minecraft mc = Minecraft.getInstance();
    public final String scriptName;

    private final List<Module> registeredModules = new ArrayList<>();
    private final List<Command> registeredCommands = new ArrayList<>();
    private final List<HudModule> registeredHudElements = new ArrayList<>();

    private final List<ScriptTask> tasks = new ArrayList<>();
    private final Object taskLock = new Object();
    private int nextTaskId = 1;

    private final Map<String, Integer> errorCounts = new HashMap<>();

    public ScriptContext(String scriptName) {
        this.scriptName = scriptName;
    }

    // --- Module Registration ---

    public ScriptModule module(String name, Category category, String description) {
        ScriptModule sm = new ScriptModule(name, description, category);
        sm.setScriptContext(this);
        registeredModules.add(sm);
        ModuleManager.INSTANCE.register(sm);
        return sm;
    }

    public ScriptModule module(String name, Category category) {
        return module(name, category, "Scripted module from " + scriptName);
    }

    public ScriptModule module(String name, String categoryName) {
        Category cat = Category.MISC;
        try {
            cat = Category.valueOf(categoryName.toUpperCase());
        } catch (Exception ignored) {}
        return module(name, cat);
    }

    // --- Custom Chat Commands ---

    public ScriptCustomCommand command(String name, String description, java.util.function.Consumer<String[]> action, String... aliases) {
        ScriptCustomCommand cmd = new ScriptCustomCommand(name, description, action, aliases);
        registeredCommands.add(cmd);
        CommandManager.INSTANCE.register(cmd);
        return cmd;
    }

    public ScriptCustomCommand command(String name, java.util.function.Consumer<String[]> action, String... aliases) {
        return command(name, "Script command from " + scriptName, action, aliases);
    }

    // --- Custom HUD Elements ---

    public ScriptHudElement hud(String name, String description, float defaultX, float defaultY) {
        ScriptHudElement hud = new ScriptHudElement(name, description, defaultX, defaultY);
        hud.setScriptContext(this);
        registeredHudElements.add(hud);
        ModuleManager.INSTANCE.register(hud);
        return hud;
    }

    public ScriptHudElement hud(String name, float defaultX, float defaultY) {
        return hud(name, "Script HUD element from " + scriptName, defaultX, defaultY);
    }

    // --- Timers ---

    /** Выполнить действие один раз через указанное число тиков (20 тиков = 1 секунда). Возвращает id задачи. */
    public int after(int ticks, Runnable action) {
        return schedule(Math.max(1, ticks), true, action);
    }

    /** Выполнять действие каждые N тиков. Возвращает id задачи. */
    public int every(int ticks, Runnable action) {
        return schedule(Math.max(1, ticks), false, action);
    }

    private int schedule(int ticks, boolean once, Runnable action) {
        if (action == null) return -1;
        ScriptTask task = new ScriptTask(nextTaskId++, ticks, once, action);
        synchronized (taskLock) {
            tasks.add(task);
        }
        return task.id;
    }

    /** Отменить задачу, созданную after()/every(), по её id. */
    public void cancelTask(int id) {
        synchronized (taskLock) {
            tasks.removeIf(t -> t.id == id);
        }
    }

    public void cancelAllTasks() {
        synchronized (taskLock) {
            tasks.clear();
        }
    }

    /** Тиковый диспетчер таймеров. Вызывается ScriptManager'ом на каждом клиентском тике. */
    public void tickTasks() {
        List<ScriptTask> snapshot;
        synchronized (taskLock) {
            snapshot = new ArrayList<>(tasks);
        }

        for (ScriptTask task : snapshot) {
            if (--task.delay > 0) continue;

            try {
                task.action.run();
            } catch (Throwable t) {
                int count = reportError("timer#" + task.id, t);
                if (count >= MAX_ERRORS) {
                    task.failed = true;
                }
            }

            if (task.once || task.failed) {
                synchronized (taskLock) {
                    tasks.remove(task);
                }
            } else {
                task.delay = task.period;
            }
        }
    }

    private static final class ScriptTask {
        final int id;
        final int period;
        final boolean once;
        final Runnable action;
        int delay;
        volatile boolean failed;

        ScriptTask(int id, int period, boolean once, Runnable action) {
            this.id = id;
            this.period = period;
            this.once = once;
            this.action = action;
            this.delay = period;
        }
    }

    // --- Error Reporting ---

    /**
     * Сообщает об ошибке скрипта: консоль + чат (не больше MAX_ERRORS сообщений на источник).
     * Возвращает текущее число ошибок данного источника.
     */
    public int reportError(String source, Throwable error) {
        int count;
        synchronized (errorCounts) {
            count = errorCounts.merge(source, 1, Integer::sum);
        }

        if (count <= MAX_ERRORS) {
            System.err.println("[Collective-Scripting] " + scriptName + " (" + source + ") error #" + count + ": "
                    + (error == null ? "unknown error" : error.toString()));
            if (error != null) {
                error.printStackTrace();
            }
            String message = error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
            ChatHelper.sendMessage(Component.literal("§c[Script] §f" + scriptName + " §7(" + source + "): §c" + message));
        }
        return count;
    }

    public int getErrorCount(String source) {
        synchronized (errorCounts) {
            return errorCounts.getOrDefault(source, 0);
        }
    }

    // --- Utility ---

    /** Найти любой модуль клиента по имени (без учёта регистра). */
    public Module getModule(String name) {
        if (name == null) return null;
        String clean = name.trim();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getName().equalsIgnoreCase(clean)) {
                return m;
            }
        }
        return null;
    }

    /** Выполнить код в основном (игровом) потоке. */
    public void runOnMainThread(Runnable action) {
        if (action != null) {
            mc.execute(action);
        }
    }

    // --- Game Automation & Actions API ---

    public void sendPacket(Packet<?> packet) {
        if (mc.getNetworkHandler() != null && packet != null) {
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    public void rotateSilent(float yaw, float pitch) {
        RotationUtils.getInstance().setRotationSilent(yaw, pitch);
    }

    public void rotateSilent(float yaw, float pitch, double turnSpeed) {
        RotationUtils.getInstance().setRotationSilent(yaw, pitch, turnSpeed);
    }

    public void rotateInstant(float yaw, float pitch) {
        RotationUtils.getInstance().setRotationSilentInstant(null, 0, yaw, pitch);
    }

    public void rotateTo(Vec3 target) {
        if (mc.player != null) {
            float[] rots = RotationUtils.getLegitRotations(target);
            RotationUtils.getInstance().setRotationSilent(rots[0], rots[1]);
        }
    }

    public void rotateTo(double x, double y, double z) {
        rotateTo(new Vec3(x, y, z));
    }

    public void pathTo(int x, int y, int z) {
        BaritoneBridge.setGoal(new BlockPos(x, y, z));
    }

    public void cancelPath() {
        BaritoneBridge.clearGoal();
    }

    public void sendChatMessage(String message) {
        if (mc.player != null && message != null) {
            if (message.startsWith("/")) {
                mc.player.networkHandler.sendChatCommand(message.substring(1));
            } else {
                mc.player.networkHandler.sendChatMessage(message);
            }
        }
    }

    public void executeClientCommand(String commandWithPrefix) {
        CommandManager.INSTANCE.execute(commandWithPrefix);
    }

    // --- Output & Logging ---

    public void print(Object msg) {
        ChatHelper.sendMessage(Component.literal("§7[§d" + scriptName + "§7] §f" + String.valueOf(msg)));
    }

    public void log(Object msg) {
        System.out.println("[" + scriptName + "] " + String.valueOf(msg));
    }

    // --- Cleanup & Lifecycle ---

    public void unregisterAll() {
        cancelAllTasks();

        ArrayList<Module> stale = new ArrayList<>();
        for (Module m : registeredModules) {
            ModuleManager.INSTANCE.unregister(m);
            stale.add(m);
        }
        registeredModules.clear();

        for (Command c : registeredCommands) {
            CommandManager.INSTANCE.getCommands().remove(c);
        }
        registeredCommands.clear();

        for (HudModule h : registeredHudElements) {
            if (!stale.contains(h)) {
                ModuleManager.INSTANCE.unregister(h);
            }
        }
        registeredHudElements.clear();

        synchronized (errorCounts) {
            errorCounts.clear();
        }
    }

    public List<Module> getRegisteredModules() {
        return registeredModules;
    }

    public List<Command> getRegisteredCommands() {
        return registeredCommands;
    }

    public List<HudModule> getRegisteredHudElements() {
        return registeredHudElements;
    }
}