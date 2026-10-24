package me.collective.mixin;

import me.collective.proxy.ProxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перепривязка кнопок Alts/Proxy к краям экрана каждый тик:
 * ванильные экраны при ресайзе перестраивают только свои виджеты.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftClientTick {

    @Inject(method = "tick", at = @At("TAIL"))
    private void repositionMenuButtons(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        Screen screen = mc.currentScreen;
        if (screen == null) return;
        boolean mp = screen instanceof MultiplayerScreen;
        if (!mp && !(screen instanceof TitleScreen)) return;
        for (Element child : screen.children()) {
            if (!(child instanceof ButtonWidget button)) continue;
            String label = button.getMessage().getString();
            if (label.equals("Alts")) {
                button.setX(screen.width - 108);
                button.setY(mp ? 6 : screen.height - 28);
            } else if (mp && label.startsWith("Proxy:")) {
                button.setX(5);
                button.setY(6);
                button.setMessage(Component.literal("Proxy: " + (ProxyManager.INSTANCE.isEnabled()
                    ? ProxyManager.INSTANCE.activeName() : "выкл")));
            }
        }
    }
}