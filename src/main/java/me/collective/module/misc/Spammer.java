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
    private String fact = null;
    private long lastSend = 0L;
    private long lastFactAttempt = 0L;

    public Spammer() {
        super("Spammer", "Флуд в чат по файлу или фактами.", Category.MISC);
    }

    private static Path spamFile() {
        return FabricLoader.getInstance().getGameDir().resolve("collective").resolve("spammer.txt");
    }

    @Override
    public void onEnable() {
        loadSpammer();
        fetchFact();
        lastSend = System.currentTimeMillis();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (System.currentTimeMillis() - lastSend < delay.getFloatValue() * 1000) return;

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
            fetchFact();
        }

        if (msg == null || msg.isBlank()) return;
        if (antiSpam.getValue()) msg += randomSuffix();

        if (spamMode.process("Chat")) {
            if (msg.charAt(0) == '/') {
                mc.getNetworkHandler().sendChatCommand(msg.substring(1));
            } else {
                mc.getNetworkHandler().sendChatMessage(global.getValue() ? "!" + msg : msg);
            }
        } else {
            PlayerEntity target = nearestPlayer();
            if (target == null) return;
            mc.getNetworkHandler().sendChatCommand(
                whisperPrefix.compute().trim() + " " + target.getGameProfile().name() + " " + msg);
        }
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

    private void fetchFact() {
        lastFactAttempt = System.currentTimeMillis();
        new Thread(() -> {
            try {
                String json;
                try (var in = new java.net.URL("https://catfact.ninja/fact?max_length=200").openStream()) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                fact = obj.get("fact").getAsString();
            } catch (Exception e) {
                fact = null;
            }
        }).start();
    }

    private static String randomSuffix() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return " [" + (char) ('a' + rnd.nextInt(26)) + rnd.nextInt(10) + (char) ('a' + rnd.nextInt(26)) + "]";
    }
}
