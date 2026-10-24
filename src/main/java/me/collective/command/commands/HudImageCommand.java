package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.ModuleManager;
import me.collective.module.hud.ImageHUD;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class HudImageCommand extends Command {
    public HudImageCommand() {
        super("hudimage", "Сменить изображение в ImageHUD.", ".hudimage <название>", "setimage", "img");
    }

    @Override
    public void execute(String[] args) {
        ImageHUD hud = ModuleManager.INSTANCE.getModule(ImageHUD.class);
        if (hud == null) return;

        if (args.length == 0) {
            ChatHelper.sendMessage(Component.literal("Использование: .hudimage <Collective|Komod1|Komod2|Komod3>")
                .styled(s -> s.withColor(ChatFormatting.GRAY)));
            return;
        }

        String choice = args[0];
        if (choice.equalsIgnoreCase("комод1")) choice = "Komod1";
        else if (choice.equalsIgnoreCase("комод2")) choice = "Komod2";
        else if (choice.equalsIgnoreCase("комод3")) choice = "Komod3";

        if (hud.image.setByName(choice)) {
            hud.info("Картинка изменена на: %s", hud.image.getValue().name());
        } else {
            hud.warning("Нет такой картинки: %s", args[0]);
        }
    }
}