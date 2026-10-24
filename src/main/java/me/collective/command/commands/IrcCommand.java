package me.collective.command.commands;

import me.collective.command.Command;
import me.collective.module.ModuleManager;
import me.collective.module.client.Irc;

/** .irc <message> — чат; .irc — статус; .irc nick <ник> — смена ника. */
public class IrcCommand extends Command {
    public IrcCommand() {
        super("irc", "Message to the shared IRC channel of Collective users.", ".irc <message> | .irc nick <name>");
    }

    @Override
    public void execute(String[] args) {
        Irc irc = ModuleManager.INSTANCE.getModule(Irc.class);
        if (irc == null) {
            sendMessage("IRC module is missing.");
            return;
        }

        if (args.length == 0) {
            sendMessage(irc.statusText());
            return;
        }

        if (args[0].equalsIgnoreCase("nick")) {
            handleNickname(irc, args);
            return;
        }

        if (args[0].equalsIgnoreCase("server") || args[0].equalsIgnoreCase("host")) {
            handleServer(irc, args);
            return;
        }

        if (args[0].equalsIgnoreCase("token") || args[0].equalsIgnoreCase("key")) {
            handleToken(irc, args);
            return;
        }

        String text = String.join(" ", args);
        if (!irc.isToggled()) {
            sendMessage("IRC is off — enable the IRC module first.");
            return;
        }
        if (!irc.sendChat(text)) {
            sendMessage(irc.statusText());
        }
    }

    private void handleNickname(Irc irc, String[] args) {
        if (args.length < 2) {
            String current = irc.nickname.getValue().trim();
            sendMessage("Ник IRC: §b" + (current.isEmpty() ? "<авто — ник акка>" : current)
                + "§f. Ставить: §7.irc nick <ник>§f.");
            return;
        }

        String normalized = irc.normalizeNick(args[1]);
        String previous = irc.nickname.getValue().trim();
        if (previous.equals(normalized)) {
            sendMessage("Ник IRC уже: §b" + (normalized.isEmpty() ? "<авто>" : normalized) + "§f.");
            return;
        }

        irc.nickname.setValue(normalized);
        sendMessage("Ник IRC теперь: §b" + (normalized.isEmpty() ? "<авто — ник акка>" : normalized)
            + "§f. Смена применится при пересоединении.");
    }

    private void handleServer(Irc irc, String[] args) {
        if (args.length < 2) {
            sendMessage("Сервер IRC: §b" + irc.server + "§7:§b" + irc.port
                + "§f. Менять: §7.irc server <host/ip> [port]§f.");
            return;
        }
        String host = args[1].trim();
        int port = irc.port;
        if (host.contains(":")) {
            String[] parts = host.split(":", 2);
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
        }
        if (args.length >= 3) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (Exception ignored) {}
        }
        irc.setServer(host, port);
        sendMessage("Сервер IRC изменён на §b" + irc.server + "§7:§b" + irc.port
            + "§f. Переподключение...");
    }

    private void handleToken(Irc irc, String[] args) {
        if (args.length < 2) {
            sendMessage("Ключ IRC (Token): §b" + irc.token + "§f. Менять: §7.irc token <ключ>§f.");
            return;
        }
        irc.setToken(args[1].trim());
        sendMessage("Ключ IRC изменён на §b" + irc.token + "§f. Переподключение...");
    }
}