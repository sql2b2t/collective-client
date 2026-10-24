package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.gui.hud.HudEditorScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class HudCommand extends Command {
    public HudCommand() {
        super("hud", "Opens HUD editor.", ".hud", "hudeditor", "huded");
    }

    @Override
    public void execute(String[] args) {
        if (mc != null) {
            mc.execute(() -> mc.gui.setScreen(HudEditorScreen.getInstance()));
            ChatHelper.sendMessage(Component.literal("Редактор HUD открыт.").withStyle(ChatFormatting.GRAY));
        }
    }
}
