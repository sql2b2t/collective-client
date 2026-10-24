package me.collective.module.client;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.util.DiscordIpc;

public class Discord extends Module {
    public static Discord INSTANCE;

    public final BooleanSetting uid = add(new BooleanSetting("UID", true));
    public final BooleanSetting server = add(new BooleanSetting("Server", true));

    private DiscordIpc ipc;
    private Thread workerThread;
    private long startTime;

    public Discord() {
        super("Discord", "Отображает статус игры в Discord Rich Presence.", Category.CLIENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        startTime = System.currentTimeMillis() / 1000L;
        ipc = new DiscordIpc();

        workerThread = new Thread(() -> {
            while (isToggled() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (ipc != null && !ipc.isConnected()) {
                        ipc.connect();
                    }

                    if (ipc != null && ipc.isConnected()) {
                        String details = "Collective Core 1.21.11";
                        if (uid.getValue() && mc.player != null) {
                            details = mc.player.getName().getString();
                        }

                        String state = "In Main Menu";
                        if (mc.getCurrentServer() != null) {
                            state = server.getValue() ? ("Playing on " + mc.getCurrentServer().ip) : "Playing Multiplayer";
                        } else if (mc.hasSingleplayerServer()) {
                            state = "Playing Singleplayer";
                        }

                        ipc.setActivity(details, state, DiscordIpc.DEFAULT_ICON, "Collective Core 1.21.11", startTime);
                    }

                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "Collective-Discord-RPC");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void onDisable() {
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        if (ipc != null) {
            ipc.close();
            ipc = null;
        }
    }
}