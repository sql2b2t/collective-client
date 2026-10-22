package me.collective.module.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Порт Spammer из ThunderHack.
 *
 * <p>Флуд в чат по файлу collective/spammer.txt (строка = сообщение)
 * или случайными фактами. Режим Whisper — ближайшему игроку.
 */
public class Spammer extends Module {
    public final ModeSetting spamMode = add(new ModeSetting("Mode", "Chat", "Chat", "Whisper"));
    public final ModeSetting messages = add(new ModeSetting("Messages", "File", "File", "CatFacts"));
    public final ModeSetting whisperPrefix = add(new ModeSetting("Prefix", "w", "w", "msg", "tell"));
    public final BooleanSetting global = add(new BooleanSetting("Global", true));
    public final BooleanSetting antiSpam = add(new BooleanSetting("AntiSpam", false));
    public final NumberSetting delay = add(new NumberSetting("Delay", 5.0, 1.0, 30.0, 0.5));

    private final List<String> spamList = new ArrayList<>();
    private volatile String fact = null;
    private long lastSend = 0L;
    private long lastFactAttempt = 0L;
    private final Object fetchLock = new Object();
    private Thread fetcher;
    private java.net.HttpURLConnection connection;
    private long fetchGeneration;
    private boolean factsMode;
    private static final int MAX_RESPONSE_BYTES = 4096;
    private static final long FETCH_DEADLINE_MS = 8000;

    public Spammer() {
        super("Spammer", "Флуд в чат по файлу или фактами.", Category.MISC);
    }

    private static Path spamFile() {
        return FabricLoader.getInstance().getGameDir().resolve("collective").resolve("spammer.txt");
    }

    @Override
    public void onEnable() {
        cancelFetch();
        loadSpammer();
        factsMode = messages.process("CatFacts");
        lastFactAttempt = 0;
        lastSend = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        cancelFetch();
    }

    @Override
    public void onUpdate() {
        boolean useFacts = messages.process("CatFacts");
        if (factsMode != useFacts) {
            cancelFetch();
            factsMode = useFacts;
            lastFactAttempt = 0;
        }
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            cancelFetch();
            return;
        }
        if (System.currentTimeMillis() - lastSend < delay.getFloatValue() * 1000) return;
        // No recipient means no request and no consumption of an already fetched fact.
        PlayerEntity target = spamMode.process("Whisper") ? nearestPlayer() : null;
        if (spamMode.process("Whisper") && target == null) {
            cancelFetch();
            return;
        }

        String msg;
        if (messages.process("File")) {
            if (spamList.isEmpty()) {
                error("Файл spammer.txt пуст! Закинь строки в collective/spammer.txt");
                setToggled(false);
                return;
            }
            msg = spamList.get(ThreadLocalRandom.current().nextInt(spamList.size()));
        } else {
            // Первый fetch мог упасть (без сети): без ретрая режим CatFacts
            // молчал бы вечно. Повторяем не чаще раза в 5 секунд.
            if (fact == null) {
                long now = System.currentTimeMillis();
                if (now - lastFactAttempt > 5000) {
                    lastFactAttempt = now;
                    fetchFact();
                }
                return;
            }
            msg = fact;
        }

        if (msg == null || msg.isBlank()) return;
        if (antiSpam.getValue()) msg += randomSuffix();
        // The protocol limits chat/command payloads to 256 characters.
        msg = msg.replaceAll("[\\p{Cntrl}]", " ");

        if (spamMode.process("Chat")) {
            if (msg.length() > 255) msg = msg.substring(0, 255);
            if (msg.charAt(0) == '/') {
                mc.getNetworkHandler().sendChatCommand(msg.substring(1));
            } else {
                mc.getNetworkHandler().sendChatMessage(global.getValue() ? "!" + msg : msg);
            }
        } else {
            String prefix = whisperPrefix.compute().trim() + " " + target.getGameProfile().name() + " ";
            if (msg.length() > 256 - prefix.length()) msg = msg.substring(0, 256 - prefix.length());
            mc.getNetworkHandler().sendChatCommand(prefix + msg);
        }
        if (useFacts) fact = null;
        lastSend = System.currentTimeMillis();
    }

    private PlayerEntity nearestPlayer() {
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void loadSpammer() {
        spamList.clear();
        try {
            Path file = spamFile();
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "Collective Client — лучший клиент для 2b2t!\n", StandardCharsets.UTF_8);
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) spamList.add(line.strip());
            }
        } catch (Exception e) {
            error("Не смог прочитать spammer.txt: " + e.getMessage());
        }
    }

    private void cancelFetch() {
        synchronized (fetchLock) {
            fetchGeneration++;
            fact = null;
            if (connection != null) connection.disconnect();
            if (fetcher != null) fetcher.interrupt();
            // Keep ownership until finally: a rapid re-enable cannot overlap workers.
        }
    }

    private void fetchFact() {
        if (!isToggled() || !messages.process("CatFacts")) return;
        synchronized (fetchLock) {
            if (fetcher != null) return;
            lastFactAttempt = System.currentTimeMillis();
            long generation = fetchGeneration;
            fetcher = new Thread(() -> {
                java.net.HttpURLConnection request = null;
                try {
                    long deadline = System.nanoTime() + FETCH_DEADLINE_MS * 1_000_000L;
                    request = (java.net.HttpURLConnection) java.net.URI.create("https://catfact.ninja/fact?max_length=200").toURL().openConnection();
                    request.setConnectTimeout(3000);
                    request.setReadTimeout(1000);
                    request.setInstanceFollowRedirects(false);
                    synchronized (fetchLock) {
                        if (generation != fetchGeneration) return;
                        connection = request;
                    }
                    if (request.getResponseCode() != 200) return;
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (var in = request.getInputStream()) {
                        byte[] buffer = new byte[512];
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) return;
                            if (bytes.size() + count > MAX_RESPONSE_BYTES) return;
                            bytes.write(buffer, 0, count);
                        }
                    }
                    if (System.nanoTime() > deadline) return;
                    JsonObject obj = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
                    String result = obj.get("fact").getAsString();
                    if (result.isBlank() || result.length() > 200 || result.chars().anyMatch(Character::isISOControl)) return;
                    synchronized (fetchLock) {
                        if (generation == fetchGeneration) fact = result;
                    }
                } catch (Exception ignored) {
                    // Retry is rate-limited by the client tick; never log remote content.
                } finally {
                    if (request != null) request.disconnect();
                    synchronized (fetchLock) {
                        connection = null;
                        fetcher = null;
                    }
                }
            }, "Collective-Spammer-Fact");
            fetcher.setDaemon(true);
            fetcher.start();
        }
    }

    private static String randomSuffix() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return " [" + (char) ('a' + rnd.nextInt(26)) + rnd.nextInt(10) + (char) ('a' + rnd.nextInt(26)) + "]";
    }
}
