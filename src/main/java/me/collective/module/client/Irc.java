package me.collective.module.client;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.emotes.EmoteEngine;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Общий чат и эмоуты пользователей Collective Client.
 *
 * <p>Сеть — простой TCP-релей на вашем VPS (server/CollectiveIRCServer.java):
 * после LOGIN все сообщения и эмоуты пересылаются каждому подключённому
 * пользователю чита. Канал виден только тем, у кого есть ключ (Token).
 *
 * <p>Эмоуты: локальный старт эмоута (колесо Emotes) рассылается всем и
 * проигрывается на чужих моделях тем же кодом {@link EmoteEngine}; стоп —
 * в том числе авто-стоп при движении — тоже синхронизируется.
 *
 * <p>Соединение живёт и на экране входа — удобно переписываться в очереди.
 */
public class Irc extends Module {
    public static Irc INSTANCE;

    public final StringSetting nickname = add(new StringSetting("Nickname", ""));
    public final BooleanSetting autoConnect = add(new BooleanSetting("AutoConnect", true));
    public final BooleanSetting showChat = add(new BooleanSetting("Chat", true));
    public final BooleanSetting shareEmotes = add(new BooleanSetting("Emotes", true));
    public final BooleanSetting showActivity = add(new BooleanSetting("Joins Quits", true));

    private static final int CONNECT_TIMEOUT_MS = 6_000;
    private static final int LOGIN_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_MISSED_PINGS = 3;
    private static final int MAX_TEXT = 240;
    private static final int MAX_LINE = 2040;
    private static final int BACKOFF_STEP_MS = 5_000;
    private static final int BACKOFF_MAX_MS = 30_000;

    /**
     * Релей вшит в клиент: адрес, порт и ключ не настраиваются — юзер задаёт
     * только ник (Nickname). Ключ ниже держит рандомных зрителей подальше.
     */
    public static final String RELAY_HOST = "31.77.19.160";
    public static final int RELAY_PORT = 29900;
    public static final String RELAY_TOKEN = "collective";

    // ── Состояние сети ──
    private Thread thread;
    private volatile Socket socket;
    private volatile OutputStreamWriter writer;
    private volatile boolean connected;
    private volatile String usedName = "";
    private volatile String lastError = "";
    private final Object sendLock = new Object();
    private final Set<String> users = ConcurrentHashMap.newKeySet();

    public Irc() {
        super("IRC", "Общий чат и эмоуты пользователей чита через релей на VPS. "
            + "Ник задаётся в Nickname (пусто = имя аккаунта).", Category.CLIENT);
        INSTANCE = this;
        // Смена ника на лету: рвём соединение, run() сам перелогинится
        // (backoff сброшен, т.к. прошлые сессии были успешными).
        this.nickname.setChangeListener(v -> {
            if (this.connected) {
                closeQuietly();
            }
        });
    }

    @Override
    public void onEnable() {
        this.connected = false;
        this.users.clear();
        this.lastError = "";
        this.usedName = currentName();

        EmoteEngine.setSyncListener(new EmoteEngine.PlaybackSync() {
            @Override
            public void onLocalStart(String emoteId) {
                if (shareEmotes.getValue() && connected) {
                    send("EMOTE " + emoteId);
                }
            }

            @Override
            public void onLocalStop(String emoteId) {
                if (connected) {
                    // Стоп шлём всегда: другие должны увидеть конец эмоута,
                    // даже если свои эмоуты наружу не расшариваем.
                    send("STOP");
                }
            }
        });

        startThread();
    }

    @Override
    public void onDisable() {
        EmoteEngine.setSyncListener(null);
        EmoteEngine.clearRemote();
        this.connected = false;
        this.users.clear();
        closeQuietly();

        Thread t = this.thread;
        this.thread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private void startThread() {
        Thread current = new Thread(this::run, "Collective-IRC");
        current.setDaemon(true);
        this.thread = current;
        current.start();
    }

    // ── Цикл соединения ─────────────────────────────────────────────────────

    private void run() {
        long nextAttempt = 0L;
        int backoff = 0;

        while (isToggled()) {
            if (System.currentTimeMillis() < nextAttempt) {
                sleepSmall();
                continue;
            }
            boolean wasConnected = false;
            try {
                wasConnected = session();
            } catch (Throwable t) {
                if (!isToggled()) break;
                this.lastError = describe(t);
            } finally {
                this.connected = false;
                this.users.clear();
                closeQuietly();
            }

            if (!isToggled()) break;

            // AutoConnect выключен — за сеанс подключения пробуем один раз.
            if (!autoConnect.getValue()) {
                break;
            }
            // Успешная сессия сбрасывает паузу: релей явно жив, обрывы-random.
            if (wasConnected) {
                backoff = 0;
            } else {
                backoff = Math.min(backoff + 1, 6);
            }
            long delay = Math.min(BACKOFF_MAX_MS, (long) BACKOFF_STEP_MS * (1 << backoff));
            nextAttempt = System.currentTimeMillis() + delay;
        }
    }

    /**
     * Одна сессия: коннект + LOGIN + цикл чтения.
     *
     * @return дошло ли до OK-ответа релея (для сброса backoff).
     */
    private boolean session() {
        Socket sock = null;
        try {
            String host = RELAY_HOST;
            int port = RELAY_PORT;

            sock = new Socket();
            this.socket = sock;
            sock.setTcpNoDelay(true);
            sock.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            sock.setSoTimeout(LOGIN_TIMEOUT_MS);

            OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8);
            this.writer = out;

            String name = currentName();
            String greeting = "LOGIN " + name + " " + RELAY_TOKEN;
            synchronized (this.sendLock) {
                out.write(greeting);
                out.write('\n');
                out.flush();
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));

            // первый ответ: OK или ERR
            String reply = readLine(reader);
            if (reply == null) {
                this.lastError = "пустой ответ от релея";
                return false;
            }
            if (reply.startsWith("ERR")) {
                this.lastError = reply.substring(3).trim();
                return false;
            }
            if (!reply.startsWith("OK")) {
                this.lastError = "непонятный ответ: " + trimDisplay(reply, 60);
                return false;
            }
            this.usedName = name;

            sock.setSoTimeout(READ_TIMEOUT_MS);
            this.lastError = "";
            this.connected = true;
            announceConnect();

            int missed = 0;
            while (isToggled() && this.connected) {
                String line;
                try {
                    line = readLine(reader);
                } catch (SocketTimeoutException ste) {
                    // Тишина 15 с: отправляем PING; PONG придёт в тот же интервал.
                    // Если тишина продолжается N раз — считаем связь мёртвой.
                    if (++missed >= MAX_MISSED_PINGS) {
                        this.lastError = "релей не отвечает на пинги";
                        this.connected = false;
                    } else {
                        send("PING");
                    }
                    continue;
                }
                if (line == null) {
                    this.lastError = "релей закрыл связь";
                    break;
                }
                missed = 0;
                if (!line.isEmpty()) {
                    handle(line);
                }
            }
            return true;
        } catch (IOException e) {
            this.lastError = describe(e);
            return false;
        } finally {
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Строка с ограничением длины (не доверяем чужим байтам). */
    private static String readLine(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder(160);
        int c = reader.read();
        if (c < 0) return null;
        while (c >= 0 && c != '\n') {
            if (c != '\r' && sb.length() < MAX_LINE) {
                sb.append((char) c);
            }
            c = reader.read();
        }
        return sb.toString();
    }

    // ── Входящие (поток читателя; UI и мир — только через mc.execute) ───────

    private void handle(String line) {
        if (line.startsWith("MSG ")) {
            int space = line.indexOf(' ', 4);
            if (space < 0 || !showChat.getValue()) return;
            String name = line.substring(4, space);
            String text = line.substring(space + 1);
            if (name.isEmpty() || text.isEmpty()) return;

            final String nameF = trimDisplay(name, 24);
            final String textF = trimDisplay(text, MAX_TEXT);
            mc.execute(() -> ChatHelper.sendMessage(
                "§8[§bIRC§8] §b" + nameF + "§f: " + textF));
            return;
        }

        if (line.startsWith("JOIN ")) {
            String name = line.substring(5).trim();
            if (name.isEmpty()) return;
            this.users.add(name);
            final String nameF = trimDisplay(name, 24);
            if (showActivity.getValue()) {
                mc.execute(() -> ChatHelper.sendMessage(
                    "§8[§bIRC§8] §7" + nameF + "§7 вошёл в канал"));
            }
            return;
        }

        if (line.startsWith("QUIT ")) {
            String name = line.substring(5).trim();
            this.users.remove(name);
            final String nameF = trimDisplay(name, 24);
            if (showActivity.getValue()) {
                mc.execute(() -> ChatHelper.sendMessage(
                    "§8[§bIRC§8] §7" + nameF + "§7 покинул канал"));
            }
            return;
        }

        if (line.startsWith("USERS ")) {
            this.users.clear();
            for (String name : line.substring(6).split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) this.users.add(trimmed);
            }
            return;
        }

        if (line.startsWith("EMOTE ")) {
            int space = line.indexOf(' ', 6);
            if (space < 0 || !shareEmotes.getValue()) return;
            String name = line.substring(6, space);
            String id = line.substring(space + 1).trim();
            if (name.isEmpty() || id.isEmpty()) return;

            final String idF = id;
            mc.execute(() -> {
                UUID uuid = playerUuidByName(name);
                if (uuid != null) {
                    EmoteEngine.playRemote(uuid, idF);
                }
            });
            return;
        }

        if (line.startsWith("STOP ")) {
            String name = line.substring(5).trim();
            if (name.isEmpty()) return;
            mc.execute(() -> {
                UUID uuid = playerUuidByName(name);
                if (uuid != null) {
                    EmoteEngine.stopRemote(uuid);
                }
            });
        }
    }

    private UUID playerUuidByName(String name) {
        try {
            if (mc.getNetworkHandler() == null) return null;
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null && name.equals(entry.getProfile().name())) {
                    return entry.getProfile().id();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ── Исходящие ───────────────────────────────────────────────────────────

    private void send(String line) {
        OutputStreamWriter out = this.writer;
        if (out == null || !this.connected) return;

        synchronized (this.sendLock) {
            try {
                out.write(line);
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                this.connected = false;
                closeQuietly();
            }
        }
    }

    /** Сообщение в общий чат (команда .irc). */
    public boolean sendChat(String raw) {
        String text = raw.trim();
        if (text.isEmpty() || text.length() > MAX_TEXT) return false;
        if (!this.connected) return false;
        send("SAY " + text);
        return true;
    }

    /** Статус для команды .irc без аргументов (адрес релея в чат не светим). */
    public String statusText() {
        if (this.connected) {
            return "IRC на связи: ник §b" + trimDisplay(usedName, 16)
                + "§f, в канале §b" + users.size() + "§f.";
        }
        if (this.thread != null && isToggled()) {
            return "IRC подбирает связь, крайний сбой: §7"
                + hideAddress(trimDisplay(lastError, 90)) + "§f.";
        }
        return "IRC выключен.";
    }

    // ── Мелочи ──────────────────────────────────────────────────────────────

    private void announceConnect() {
        mc.execute(() -> ChatHelper.sendMessage(
            "§8[§bIRC§8] §aСвязь с релеем установлена, ник: §b" + usedName));
    }

    private static String trimDisplay(String source, int limit) {
        return source.length() <= limit ? source : source.substring(0, limit) + "…";
    }

    private static String baseName() {
        String name = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.getSession() != null) {
                name = client.getSession().getUsername();
            }
        } catch (Throwable ignored) {
        }
        if (name == null || name.isBlank()) name = "Player";
        return name;
    }

    /** Ник для релея: настройка Nickname, иначе имя аккаунта Minecraft. */
    private String currentName() {
        String custom = this.nickname.getValue().trim();
        return sanitizeName(custom.isEmpty() ? baseName() : custom);
    }

    /** Публичный нормализатор (команда .irc nick): к правилам релея; < 3
     * валидных символов = "" → смысл "авто, ник аккаунта". */
    public String normalizeNick(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length() && sb.length() < 16; i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_';
            if (ok) {
                sb.append(c);
            }
        }
        return sb.length() >= 3 ? sb.toString() : "";
    }

    /** Ник под правила релея [A-Za-z0-9_]{3,16}; кириллица вырезается. */
    private static String sanitizeName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 16; i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_';
            if (ok) {
                sb.append(c);
            }
        }
        String name = sb.toString();
        return name.length() >= 3 ? name : "user" + name;
    }

    /** Замазать IP/адрес релея в тексте, уходящем в видимый чат. */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b");

    private String hideAddress(String text) {
        String result = IPV4_PATTERN.matcher(text).replaceAll("*.*.*.*");
        String hostValue = RELAY_HOST;
        if (!hostValue.isEmpty()) {
            result = result.replace(hostValue, "***");
        }
        return result;
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message != null && !message.isBlank()
            ? message
            : t.getClass().getSimpleName();
    }

    private void closeQuietly() {
        Socket sock = this.socket;
        this.socket = null;
        this.writer = null;
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sleepSmall() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String getInfoString() {
        if (!isToggled()) return null;
        return this.connected ? ("IRC " + users.size()) : "IRC …";
    }
}
