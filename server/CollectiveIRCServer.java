/*
 * Collective IRC — релей чата и эмоутов для пользователей Collective Client.
 *
 * Разворачивается одним файлом на любом JDK 11+:
 *   java CollectiveIRCServer.java <port> <token>
 *
 * Протокол (UTF-8, одна команда на строку):
 *   клиент -> сервер:
 *     LOGIN <name> <token>   — первый пакет в сессии
 *     SAY <text>             — сообщение в общий чат
 *     EMOTE <id>             — старт эмоута
 *     STOP                   — стоп эмоута
 *     PING                   — keepalive
 *   сервер -> клиент:
 *     OK / ERR <причина>     — ответ на LOGIN
 *     USERS <перечень имён>  — полный список после JOIN/QUIT
 *     JOIN <name> / QUIT <name>
 *     MSG <name> <text>
 *     EMOTE <name> <id>
 *     STOP <name>
 *
 * Лимиты: имя [A-Za-z0-9_]{3,16}, текст <= 240 символов, bucket'ы
 * против флуда, исходящий буфер на клиента (переполнение = отсекается).
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CollectiveIRCServer {

    private static final int MAX_CLIENTS = 256;
    private static final int MAX_LINE = 2040;
    private static final int MAX_TEXT = 240;

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern EMOTE_ID = Pattern.compile("^[a-z0-9_]{1,48}$");

    /** Порт и ключ доступа, конфигурируются из main по argv. */
    private static int port = 29900;
    private static String token = "collective";

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (args.length >= 2) {
            String given = args[1];
            token = given == null ? "" : given.trim();
        }

        System.out.println("[CollectiveIRC] starting on port " + port
            + " token=" + (token.isEmpty() ? "<none>" : "<set>"));

        try (ServerSocket server = new ServerSocket(port)) {
            server.setReuseAddress(true);
            while (true) {
                try {
                    Socket socket = server.accept();
                    if (clients.size() >= MAX_CLIENTS) {
                        socket.close();
                        continue;
                    }
                    new ClientHandler(socket).start();
                } catch (IOException e) {
                    System.out.println("[CollectiveIRC] accept: " + e);
                }
            }
        } catch (IOException e) {
            System.out.println("[CollectiveIRC] cannot listen: " + e);
            System.exit(1);
        }
    }

    // ── Токен-ведро против флуда ─────────────────────────────────────────────

    static final class Bucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long last = System.nanoTime();

        Bucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
        }

        boolean take() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - last) / 1e9 * refillPerSecond);
            last = now;
            if (tokens < 1.0) return false;
            tokens -= 1.0;
            return true;
        }
    }

    // ── Клиент ───────────────────────────────────────────────────────────────

    private static final class ClientHandler implements Runnable {
        private final Socket socket;
        private final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<>(256);
        private final Bucket sayBudget = new Bucket(20.0, 2.0);
        private final Bucket emoteBudget = new Bucket(6.0, 0.5);
        private volatile String name = null;
        private volatile boolean alive = true;
        private Thread writer;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void start() {
            Thread t = new Thread(this, "IRC-reader");
            t.setDaemon(false);
            t.start();
        }

        // Читатель с ограничением длины строки (иначе длинная строка без \n
        // раздула бы StringBuilder).
        private static String readLine(BufferedReader r) throws IOException {
            StringBuilder sb = new StringBuilder(MAX_LINE);
            int c = r.read();
            if (c < 0) return null;
            while (c >= 0 && c != '\n') {
                if (c != '\r' && sb.length() < MAX_LINE) {
                    sb.append((char) c);
                }
                c = r.read();
            }
            return sb.toString();
        }

        /** Прямо в сокет: только при отказе в логине, когда писателя ещё нет. */
        private void direct(String line) {
            try {
                OutputStreamWriter w = new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8);
                w.write(line);
                w.write('\n');
                w.flush();
            } catch (IOException ignored) {
            }
        }

        @Override
        public void run() {
            InetAddress addr = socket.getInetAddress();
            try {
                socket.setTcpNoDelay(true);
                // 10 с на LOGIN: подключился и молчит — отсекаем
                socket.setSoTimeout(10_000);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                String greet = readLine(in);
                if (greet == null || !greet.startsWith("LOGIN ")) {
                    socket.close();
                    return;
                }
                String rest = greet.substring("LOGIN ".length()).trim();
                int space = rest.indexOf(' ');
                String name = space < 0 ? "" : rest.substring(0, space);
                String loginToken = space < 0 ? "" : rest.substring(space + 1);

                if (!NAME.matcher(name).matches()) {
                    direct("ERR bad name (allowed: A-Za-z0-9_, 3..16)");
                    socket.close();
                    return;
                }
                if (!token.isEmpty() && !loginToken.equals(token)) {
                    direct("ERR wrong token");
                    socket.close();
                    return;
                }
                if (byName(name) != null) {
                    direct("ERR name in use");
                    socket.close();
                    return;
                }

                this.name = name;
                socket.setSoTimeout(120_000);
                clients.add(this);
                System.out.println("[CollectiveIRC] join " + name + " from "
                    + addr.getHostAddress() + " (" + clients.size() + " online)");

                this.outWriter = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                enqueue("OK");
                enqueue("USERS " + namesCsv());
                broadcast("JOIN " + name, this);

                writer = new Thread(() -> pumpOut(), "IRC-writer-" + name);
                writer.setDaemon(true);
                writer.start();

                try {
                    String line;
                    while (alive && (line = readLine(in)) != null) {
                        handle(line);
                    }
                } catch (SocketTimeoutException ignored) {
                    // 120 с без входящего трафика — клиент умер
                }
            } catch (IOException e) {
                // обрыв по сети — штатный уход
            } finally {
                disconnect();
            }
        }

        private void handle(String line) {
            if (!alive) return;
            String payload = line.startsWith("SAY ") ? line.substring(4)
                : line.startsWith("EMOTE ") ? line.substring(6) : null;

            if (line.equals("PING")) {
                enqueue("PONG");
                return;
            }
            if (line.equals("STOP")) {
                broadcast("STOP " + this.name, this);
                return;
            }
            if (line.startsWith("SAY ") && payload != null) {
                if (!sayBudget.take()) return;
                String text = sanitize(payload);
                if (!text.isEmpty()) {
                    broadcast("MSG " + this.name + " " + text, null);
                }
                return;
            }
            if (line.startsWith("EMOTE ") && payload != null) {
                if (!emoteBudget.take()) return;
                String id = payload.trim().toLowerCase(Locale.ROOT);
                if (EMOTE_ID.matcher(id).matches()) {
                    broadcast("EMOTE " + this.name + " " + id, null);
                }
            }
        }

        private Writer outWriter;

        private void pumpOut() {
            try {
                while (alive) {
                    String line = out.poll(15, TimeUnit.SECONDS);
                    if (line == null) {
                        continue;
                    }
                    outWriter.write(line);
                    outWriter.write('\n');
                    outWriter.flush();
                }
            } catch (InterruptedException | IOException e) {
                // сердцебиение не ритмично или сокет умер — читатель отсечёт
            }
        }

        private void enqueue(String line) {
            if (!out.offer(line)) {
                disconnect();
            }
        }

        private void disconnect() {
            alive = false;
            if (writer != null) {
                writer.interrupt();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            if (clients.remove(this) && name != null) {
                System.out.println("[CollectiveIRC] quit " + name
                    + " (" + clients.size() + " online)");
                broadcast("QUIT " + name, this);
            }
        }
    }

    // ── Общий фасад ──────────────────────────────────────────────────────────

    private static void broadcast(String line, ClientHandler except) {
        for (ClientHandler client : clients) {
            if (client != except) {
                client.enqueue(line);
            }
        }
    }

    private static ClientHandler byName(String name) {
        for (ClientHandler client : clients) {
            if (name.equals(client.name)) {
                return client;
            }
        }
        return null;
    }

    private static String namesCsv() {
        Set<String> names = new LinkedHashSet<>();
        for (ClientHandler client : clients) {
            names.add(client.name);
        }
        return String.join(",", names);
    }

    private static String sanitize(String raw) {
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_TEXT));
        for (int i = 0; i < raw.length() && sb.length() < MAX_TEXT; i++) {
            char c = raw.charAt(i);
            if (c == '\r' || c == '\n') {
                c = ' ';
            }
            if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** Утилита для лога: текущие онлайн-имена. */
    private static List<String> namesList() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : clients) {
            names.add(client.name);
        }
        return names;
    }
}
