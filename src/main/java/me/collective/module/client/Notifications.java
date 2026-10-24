package me.collective.module.client;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.network.chat.Component;

import java.awt.Color;

public class Notifications extends Module {
    public static Notifications INSTANCE;

    public final ColorSetting watermark = add(new ColorSetting("Watermark", new Color(189, 153, 255, 255)));
    public final ColorSetting brackets = add(new ColorSetting("Brackets", new Color(255, 255, 255, 255)));
    public final BooleanSetting modules = add(new BooleanSetting("Modules", true));
    public final BooleanSetting totemPops = add(new BooleanSetting("TotemPops", true));
    public final BooleanSetting self = add(new BooleanSetting("Self", false));
    public final BooleanSetting sayChat = add(new BooleanSetting("SayChat", false));
    public final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 5.0, 0.1));

    public Notifications() {
        super("Notifications", "Всплывающие уведомления о событиях, модулях и сообщениях.", Category.CLIENT);
        INSTANCE = this;
        setToggled(true);
    }

    public void notifyModule(Module module, boolean enabled) {
        if (!isToggled() || !modules.getValue()) return;
        if (module == this) return;
        // Не спамим про открытие/закрытие интерфейса
        if (module instanceof UI) return;

        String state = enabled ? "§aenabled" : "§cdisabled";
        String msg = "§f" + module.getName() + " §7has been " + state + "§7.";
        me.collective.command.ChatHelper.sendMessage(Component.literal(msg));
    }

    public void notifyTotemPop(String name, int pops) {
        if (!isToggled() || !totemPops.getValue()) return;

        String msg = "§f" + name + " §7has popped §d" + pops + " §7totem" + (pops > 1 ? "s" : "") + "!";
        me.collective.command.ChatHelper.sendMessage(Component.literal(msg));
    }
}