package me.collective.module.misc;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.setting.ModeSetting;
import me.collective.setting.StringSetting;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/** AuthMe automation: credentials are sent only to an explicitly trusted server. */
public class AutoAuth extends Module {
    public final ModeSetting authMode = add(new ModeSetting("Mode", "Custom", "Custom", "Random", "Qwerty"));
    public final StringSetting password = add(new StringSetting("Password", "changeme"));
    public final StringSetting trustedServer = add(new StringSetting("TrustedServer", ""));

    private static final Pattern LOGIN_HINT = Pattern.compile("(^|\\s)/l(\\s|$)");
    private static final long AUTH_COOLDOWN_MS = 3000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private long lastAuthAt;
    private String lastAuthCommand = "";
    private Object lastConnection;

    public AutoAuth() {
        super("AutoAuth", "Авто-регистрация и вход только на доверенном сервере.", Category.MISC);
    }

    @Override
    public void onEnable() {
        lastAuthAt = 0;
        lastAuthCommand = "";
        lastConnection = null;
        warning("Укажи TrustedServer (адрес:порт). Пароли хранятся локально открытым текстом; не передавай конфиг и accounts-v2.properties.");
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof GameMessageS2CPacket packet)) return;
        var connection = mc.getNetworkHandler();
        if (connection == null || mc.player == null) return;
        mc.execute(() -> {
            // A queued hint from an old connection must never authenticate a new server.
            if (mc.getNetworkHandler() == connection) handleAuthMessage(packet);
        });
    }

    private void handleAuthMessage(GameMessageS2CPacket packet) {
        if (!isToggled() || mc.getNetworkHandler() == null || mc.player == null) return;
        var server = mc.getCurrentServerEntry();
        if (server == null) return;
        String serverKey = normalizeServer(server.address);
        if (serverKey.isEmpty() || !serverKey.equals(normalizeServer(trustedServer.getValue()))) return;

        String message = packet.content().getString().toLowerCase(Locale.ROOT);
        boolean register = message.contains("/reg") || message.contains("зарегистрируйтесь");
        boolean login = message.contains("авторизуйтесь") || message.contains("/login") || LOGIN_HINT.matcher(message).find();
        if (!register && !login) return;
        if (lastConnection != mc.getNetworkHandler()) {
            lastConnection = mc.getNetworkHandler();
            lastAuthCommand = "";
            lastAuthAt = 0;
        }
        long now = System.currentTimeMillis();
        String command = register ? "reg" : "login";
        if (command.equals(lastAuthCommand) && now - lastAuthAt < AUTH_COOLDOWN_MS) return;
        String pass = resolvePassword(serverKey);
        if (pass == null) return;
        lastAuthAt = now;
        lastAuthCommand = command;
        mc.getNetworkHandler().sendChatCommand(register ? "reg " + pass + " " + pass : "login " + pass);
        // Never echo a credential, even if an old config has ShowPassword enabled.
        info(register ? "Отправлена регистрация." : "Отправлен вход.");
    }

    static String normalizeServer(String address) {
        if (address == null) return "";
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";
        if (!normalized.contains(":")) return normalized + ":25565";
        if (normalized.startsWith("[") && normalized.endsWith("]")) return normalized + ":25565";
        return normalized;
    }

    private String resolvePassword(String serverKey) {
        String nick = mc.getUser().getUsername().toLowerCase(Locale.ROOT);
        if (nick.isBlank()) return null;
        String pass;
        if (authMode.process("Random")) {
            // Legacy nick:password entries are deliberately NOT imported across servers.
            Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("collective").resolve("accounts-v2.properties");
            try {
                Properties accounts = new Properties();
                if (Files.exists(file)) {
                    if (Files.size(file) > 1_048_576) throw new java.io.IOException("Credential store too large");
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        accounts.load(reader);
                    }
                }
                String key = serverKey + "|" + nick;
                pass = accounts.getProperty(key);
                if (pass == null) {
                    pass = randomPassword();
                    accounts.setProperty(key, pass);
                    Files.createDirectories(file.getParent());
                    Path temporary = Files.createTempFile(file.getParent(), "accounts-", ".tmp");
                    try {
                        try {
                            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                        } catch (UnsupportedOperationException ignored) {
                            // Non-POSIX hosts use their platform ACLs.
                        }
                        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                            accounts.store(writer, "Server and account scoped AutoAuth credentials; do not share");
                        }
                        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
            } catch (Exception ignored) {
                error("Не удалось безопасно сохранить/прочитать пароль; команда не отправлена.");
                return null;
            }
        } else {
            pass = authMode.process("Qwerty") ? "qwerty123" : password.getValue();
        }
        if (pass == null || pass.isEmpty() || pass.length() > 100 || pass.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            error("Пароль должен содержать 1–100 символов без пробелов и управляющих символов.");
            return null;
        }
        return pass;
    }

    private static String randomPassword() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder(24);
        for (int i = 0; i < 24; i++) result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return result.toString();
    }
}