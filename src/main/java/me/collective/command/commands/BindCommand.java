package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class BindCommand extends Command {
    public BindCommand() {
        super("bind", "Binds a module to a key.", ".bind <set|clear> <module> [key]", "b");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            ChatHelper.sendMessage(Text.literal("Usage: " + getSyntax()).styled(s -> s.withColor(Formatting.RED)));
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("clear")) {
            if (args[1].equalsIgnoreCase("all")) {
                for (Module m : ModuleManager.INSTANCE.getModules()) {
                    m.getKeybind().setValue(GLFW.GLFW_KEY_UNKNOWN);
                }
                ChatHelper.sendMessage(Text.literal("Bind for all modules has been reset").styled(s -> s.withColor(Formatting.GRAY)));
                return;
            }
            Module mod = ModuleManager.INSTANCE.getModule(args[1]);
            if (mod == null) {
                ChatHelper.sendMessage(Text.literal("Module not found: " + args[1]).styled(s -> s.withColor(Formatting.RED)));
                return;
            }
            mod.getKeybind().setValue(GLFW.GLFW_KEY_UNKNOWN);
            ChatHelper.sendMessage(Text.literal("Bind for ").styled(s -> s.withColor(Formatting.GRAY))
                .append(Text.literal(mod.getName()).styled(s -> s.withColor(ChatHelper.getPrimaryTextColor())))
                .append(Text.literal(" has been reset").styled(s -> s.withColor(Formatting.GRAY))));
            return;
        }

        // .bind set <module> <key>  or  .bind <module> <key>
        String modName = sub.equals("set") ? args[1] : args[0];
        String keyStr = sub.equals("set") ? (args.length > 2 ? args[2] : "none") : args[1];

        Module mod = ModuleManager.INSTANCE.getModule(modName);
        if (mod == null) {
            ChatHelper.sendMessage(Text.literal("Module not found: " + modName).styled(s -> s.withColor(Formatting.RED)));
            return;
        }

        if (keyStr.equalsIgnoreCase("none") || keyStr.equalsIgnoreCase("clear") || keyStr.equals("0")) {
            mod.getKeybind().setValue(GLFW.GLFW_KEY_UNKNOWN);
            ChatHelper.sendMessage(Text.literal("Bind for ").styled(s -> s.withColor(Formatting.GRAY))
                .append(Text.literal(mod.getName()).styled(s -> s.withColor(ChatHelper.getPrimaryTextColor())))
                .append(Text.literal(" has been reset").styled(s -> s.withColor(Formatting.GRAY))));
            return;
        }

        try {
            int code = -1;
            String clean = keyStr.toLowerCase().replace("_", "").replace("-", "");
            if (clean.equals("rshift") || clean.equals("rightshift")) {
                code = GLFW.GLFW_KEY_RIGHT_SHIFT;
            } else if (clean.equals("lshift") || clean.equals("leftshift") || clean.equals("shift")) {
                code = GLFW.GLFW_KEY_LEFT_SHIFT;
            } else if (clean.equals("rctrl") || clean.equals("rightctrl") || clean.equals("rightcontrol")) {
                code = GLFW.GLFW_KEY_RIGHT_CONTROL;
            } else if (clean.equals("lctrl") || clean.equals("leftctrl") || clean.equals("leftcontrol") || clean.equals("ctrl")) {
                code = GLFW.GLFW_KEY_LEFT_CONTROL;
            } else if (clean.equals("ralt") || clean.equals("rightalt")) {
                code = GLFW.GLFW_KEY_RIGHT_ALT;
            } else if (clean.equals("lalt") || clean.equals("leftalt") || clean.equals("alt")) {
                code = GLFW.GLFW_KEY_LEFT_ALT;
            } else if (clean.equals("esc") || clean.equals("escape")) {
                code = GLFW.GLFW_KEY_ESCAPE;
            } else if (clean.equals("space") || clean.equals("spacebar")) {
                code = GLFW.GLFW_KEY_SPACE;
            } else if (clean.equals("tab")) {
                code = GLFW.GLFW_KEY_TAB;
            }

            if (code == -1) {
                try {
                    InputUtil.Key key = InputUtil.fromTranslationKey("key.keyboard." + keyStr.toLowerCase());
                    code = key.getCode();
                } catch (Exception ignored) {}
            }

            if (code == -1) {
                code = GLFW.class.getField("GLFW_KEY_" + keyStr.toUpperCase()).getInt(null);
            }

            mod.getKeybind().setValue(code);
            ChatHelper.sendMessage(Text.literal("Bind for ").styled(s -> s.withColor(Formatting.GRAY))
                .append(Text.literal(mod.getName()).styled(s -> s.withColor(ChatHelper.getPrimaryTextColor())))
                .append(Text.literal(" has been set to ").styled(s -> s.withColor(Formatting.GRAY)))
                .append(Text.literal(mod.getKeybind().getKeyName().toUpperCase()).styled(s -> s.withColor(Formatting.GREEN))));
        } catch (Exception e) {
            ChatHelper.sendMessage(Text.literal("Invalid key: " + keyStr).styled(s -> s.withColor(Formatting.RED)));
        }
    }
}
