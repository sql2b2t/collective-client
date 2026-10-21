package me.collective.command.commands;

import me.collective.command.Command;
import me.collective.util.DigLog;

public class DigLogCommand extends Command {
    public DigLogCommand() {
        super("diglog", "Диагностика исходящих dig-пакетов.", ".diglog");
    }

    @Override
    public void execute(String[] args) {
        boolean on = DigLog.toggle();
        sendMessage(on ? "§7[DigLog] §aвключён — копай несколько секунд и смотри строки в чате"
                       : "§7[DigLog] §cвыключен");
    }
}
