package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Music extends HudModule {
    private static final Pattern DBUS_NAME_PATTERN = Pattern.compile("string \"(org\\.mpris\\.MediaPlayer2\\.[^\"]+)\"");
    private static final Pattern TITLE_PATTERN = Pattern.compile("string \"xesam:title\"\\s+variant\\s+string \"([^\"]+)\"");
    private static final Pattern ARTIST_PATTERN = Pattern.compile("string \"xesam:artist\"\\s+variant\\s+array \\[\\s+string \"([^\"]+)\"");

    private volatile String currentTrack = "No music playing";
    private Thread pollerThread;

    public Music() {
        super("Music", "Отображает название текущего играющего музыкального трека.", 4, 360);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        startPoller();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        stopPoller();
    }

    private void startPoller() {
        stopPoller();
        pollerThread = new Thread(() -> {
            while (isToggled() && !Thread.currentThread().isInterrupted()) {
                try {
                    String track = fetchCurrentTrack();
                    if (track != null && !track.isBlank()) {
                        this.currentTrack = track;
                    } else {
                        this.currentTrack = "No music playing";
                    }
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
        }, "Collective-Music-Poller");
        pollerThread.setDaemon(true);
        pollerThread.start();
    }

    private void stopPoller() {
        if (pollerThread != null) {
            pollerThread.interrupt();
            pollerThread = null;
        }
    }

    private String fetchCurrentTrack() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return fetchWindowsTrack();
        } else {
            return fetchLinuxTrack();
        }
    }

    private String fetchLinuxTrack() {
        try {
            // First check if playerctl exists
            ProcessBuilder pbList = new ProcessBuilder("dbus-send", "--session", "--dest=org.freedesktop.DBus",
                "--type=method_call", "--print-reply", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames");
            Process proc = pbList.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }
            if (!proc.waitFor(800, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
            }

            Matcher mNames = DBUS_NAME_PATTERN.matcher(out.toString());
            while (mNames.find()) {
                String service = mNames.group(1);
                ProcessBuilder pbMeta = new ProcessBuilder("dbus-send", "--session", "--dest=" + service,
                    "--type=method_call", "--print-reply", "/org/mpris/MediaPlayer2",
                    "org.freedesktop.DBus.Properties.Get", "string:org.mpris.MediaPlayer2.Player", "string:Metadata");
                Process metaProc = pbMeta.start();
                StringBuilder metaOut = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(metaProc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        metaOut.append(line).append("\n");
                    }
                }
                if (!metaProc.waitFor(800, TimeUnit.MILLISECONDS)) {
                    metaProc.destroyForcibly();
                }

                String metaStr = metaOut.toString();
                Matcher mTitle = TITLE_PATTERN.matcher(metaStr);
                if (mTitle.find()) {
                    String title = mTitle.group(1).trim();
                    if (!title.isEmpty()) {
                        Matcher mArtist = ARTIST_PATTERN.matcher(metaStr);
                        String artist = mArtist.find() ? mArtist.group(1).trim() : "";
                        if (!artist.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(artist.toLowerCase(Locale.ROOT))) {
                            return artist + " - " + title;
                        }
                        return title;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String fetchWindowsTrack() {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "(Get-Process spotify, chrome, msedge, firefox, vlc -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object -First 1 -ExpandProperty MainWindowTitle)");
            Process proc = pb.start();
            String title = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        title = line;
                        break;
                    }
                }
            }
            if (!proc.waitFor(1200, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
            }
            return title;
        } catch (Exception ignored) {
        }
        return null;
    }

    public void setTrack(String track) {
        this.currentTrack = track;
    }

    @Override
    public void render(GuiGraphics context, float delta) {
        if (!isToggled()) return;

        String track = currentTrack != null ? currentTrack : "No music playing";
        String text = "♪ §f" + track;
        this.width = FontManager.INSTANCE.getStringWidth(text) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, text, x + 3, y + 2, new Color(200, 150, 255));
    }
}