package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

import me.collective.module.misc.AutoReconnect;

/**
 * Порт AfkLogout: выходит с сервера у заданных координат.
 * (AutoReconnect не переподключается после этого выхода — модуль
 * помечается через markAfkLogout.)
 */
public class AfkLogout extends Module {
    public enum Dim {
        Overworld,
        Nether,
        End
    }

    public final EnumSetting<Dim> dimension = add(new EnumSetting<>("Dimension", Dim.Overworld));
    public final NumberSetting xCoord = add(new NumberSetting("X", 1000, -29999983, 29999983, 1));
    public final NumberSetting zCoord = add(new NumberSetting("Z", 1000, -29999983, 29999983, 1));
    public final NumberSetting radius = add(new NumberSetting("Radius", 64, 0, 100, 1));
    public final BooleanSetting autoToggle = add(new BooleanSetting("AutoToggle", true));

    public AfkLogout() {
        super("AfkLogout", "Logs out when you reach certain coords. Useful for afk travelling.", Category.MISC);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (xCoordsMatch() && zCoordsMatch() && dimensionMatches()) {
            if (autoToggle.getValue()) setToggled(false);
            AutoReconnect.markAfkLogout();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().onDisconnect(new DisconnectS2CPacket(Text.literal("[AfkLogout] Arrived at destination.")));
            }
        }
    }

    private boolean dimensionMatches() {
        String dim = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
        return switch (dimension.getValue()) {
            case Nether -> dim.endsWith("the_nether");
            case End -> dim.endsWith("the_end");
            default -> dim.endsWith("overworld");
        };
    }

    private boolean xCoordsMatch() {
        double x = xCoord.getValue();
        double r = radius.getValue();
        return mc.player.getX() <= x + r && mc.player.getX() >= x - r;
    }

    private boolean zCoordsMatch() {
        double z = zCoord.getValue();
        double r = radius.getValue();
        return mc.player.getZ() <= z + r && mc.player.getZ() >= z - r;
    }
}
