package me.collective.scripting;

import me.collective.command.Command;
import java.util.function.Consumer;

public class ScriptCustomCommand extends Command {
    private final Consumer<String[]> action;

    public ScriptCustomCommand(String name, String description, Consumer<String[]> action, String... aliases) {
        super(name, description, "." + name, aliases);
        this.action = action;
    }

    @Override
    public void execute(String[] args) {
        if (action != null) {
            action.accept(args);
        }
    }
}