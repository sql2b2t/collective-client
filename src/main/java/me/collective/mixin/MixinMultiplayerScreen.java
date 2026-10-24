package me.collective.mixin;

import me.collective.gui.proxy.ProxyScreen;
import me.collective.proxy.ProxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Кнопки Alt Manager и выбора прокси в меню серверов. */
@Mixin(MultiplayerScreen.class)
public abstract class MixinMultiplayerScreen {

    @Inject(method = "init", at = @At("TAIL"))
    private void addAltProxyButtons(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        ScreenAccessor accessor = (ScreenAccessor) self;
        accessor.invokeAddDrawableChild(ButtonWidget.builder(Component.literal("Alts"), button ->
            Minecraft.getInstance().setScreen(new me.collective.gui.alt.AltManagerScreen(self))
        ).dimensions(self.width - 108, 6, 100, 20).build());

        accessor.invokeAddDrawableChild(ButtonWidget.builder(
            proxyLabel(), button ->
            Minecraft.getInstance().setScreen(new ProxyScreen(self))
        ).dimensions(5, 6, 100, 20).build());
    }

    @Unique
    private static Component proxyLabel() {
        return Component.literal("Proxy: " + (ProxyManager.INSTANCE.isEnabled()
            ? ProxyManager.INSTANCE.activeName() : "выкл"));
    }
}