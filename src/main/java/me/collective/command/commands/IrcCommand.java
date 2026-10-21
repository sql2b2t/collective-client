package me.collective.command.commands;

import me.collective.command.Command;
import me.collective.module.ModuleManager;
import me.collective.module.client.Irc;

/** .irc <message> — сообщение в общий чат пользователей чита; .irc — статус. */
public class IrcCommand extends Command {
    public IrcCommand() {
        super("irc", "Message to the shared IRC channel of Collective users.", ".irc <message>");
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

        String text = String.join(" ", args);
        if (!irc.isToggled()) {
            sendMessage("IRC is off — enable the IRC module first.");
            return;
        }
        if (!irc.sendChat(text)) {
            sendMessage(irc.statusText());
        }
    }
}
