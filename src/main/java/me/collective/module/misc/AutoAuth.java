package me.collective.module.misc;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.StringSetting;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Порт AutoAuth из ThunderHack.
 *
 * <p>Авто-регистрация и авто-логин (/reg, /login) на серверах с AuthMe.
 * ВНИМАНИЕ: пароль хранится в конфиге открытым текстом — перед передачей
 * конфига выключи модуль (он сотрёт пароль)!
 */
public class AutoAuth extends Module {
    public final ModeSetting authMode = add(new ModeSetting("Mode", "Custom", "Custom", "Random", "Qwerty"));
    public final StringSetting password = add(new StringSetting("Password", "changeme"));
    public final BooleanSetting showPassword = add(new BooleanSetting("ShowPassword", true));

    public AutoAuth() {
        super("AutoAuth", "Авто-регистрация и вход (/reg, /login).", Category.MISC);
    }

    @Override
    public void onEnable() {
        warning("Пароль хранится в конфиге! Перед передачей конфига ВЫКЛЮЧИ модуль!");
    }

    @Override
    public void onDisable() {
        info("Стираю пароль...");
        password.setValue("none");
    }

    private static final Pattern LOGIN_HINT = Pattern.compile("(^|\\s)/l(\\s|$)");
    private static final long AUTH_COOLDOWN_MS = 3000L;

    private long lastAuthAt;
    private String lastAuthCommand = "";

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof GameMessageS2CPacket pac)) return;
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        // Пакеты приходят на netty-треде: чтение файла, чат и команды
        // выполняем в главном потоке игры.
        mc.execute(() -> handleAuthMessage(pac));
    }

    private void handleAuthMessage(GameMessageS2CPacket pac) {
        if (!isToggled() || mc.getNetworkHandler() == null || mc.player == null) return;

        String m = pac.content().getString().toLowerCase(Locale.ROOT);

        boolean register = m.contains("/reg") || m.contains("/register") || m.contains("зарегистрируйтесь");
        boolean login = m.contains("авторизуйтесь") || m.contains("/login") || LOGIN_HINT.matcher(m).find();
        if (!register && !login) return;

        // Сервер может продублировать подсказку — не спамим командами.
        long now = System.currentTimeMillis();
        String command = register ? "reg" : "login";
        if (command.equals(this.lastAuthCommand) && now - this.lastAuthAt < AUTH_COOLDOWN_MS) return;

        String pass = resolvePassword();
        if (pass == null) return;

        this.lastAuthAt = now;
        this.lastAuthCommand = command;

        if (register) {
            mc.getNetworkHandler().sendChatCommand("reg " + pass + " " + pass);
            if (showPassword.getValue()) info("Твой пароль: " + pass);
            info("Выполнена регистрация!");
        } else {
            mc.getNetworkHandler().sendChatCommand("login " + pass);
            info("Выполнен вход!");
        }
    }

    /** Пароль из accounts.txt либо из режима настроек. */
    private String resolvePassword() {
        String accountNick = currentNick();
        String accountPass = accounts().get(accountNick.toLowerCase(Locale.ROOT));
        if (accountPass != null) return accountPass;

        return switch (authMode.compute().trim()) {
            case "Qwerty" -> "qwerty123";
            case "Random" -> randomPassword();
            default -> {
                String pass = password.getValue();
                if (pass == null || pass.isEmpty()) {
                    error("Ошибка регистрации: пароль пуст!");
                    yield null;
                }
                yield pass;
            }
        };
    }

    private static String randomPassword() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append((char) ('a' + rnd.nextInt(26)));
        for (int i = 0; i < 5; i++) sb.append((char) ('!' + rnd.nextInt(90)));
        return sb.toString();
    }

    /** Ник текущего аккаунта. */
    private String currentNick() {
        try {
            return mc.getSession().getUsername();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Пароли по аккаунтам из collective/accounts.txt (строки ник:пароль).
     * Ник из файла имеет приоритет над настройкой Mode.
     */
    private static Map<String, String> accounts() {
        Map<String, String> map = new HashMap<>();
        try {
            Path file = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir().resolve("collective").resolve("accounts.txt");
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file,
                    "# Ник:пароль — по одному на строку\n# Пример:\n# Steve:qwerty123\n",
                    StandardCharsets.UTF_8);
                return map;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sep = line.indexOf(':');
                if (sep <= 0) continue;
                String nick = line.substring(0, sep).strip().toLowerCase(Locale.ROOT);
                String pass = line.substring(sep + 1).strip();
                if (!nick.isEmpty() && !pass.isEmpty()) map.put(nick, pass);
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
