package me.collective.mixin;

import me.collective.module.player.AutoSign;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехват открытия экранов. AutoSign: если текст уже запомнен, экран
 * редактирования таблички не открывается — сразу ставится пакет записи.
 */
@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClientScreen {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void collective$autoSign(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof AbstractSignEditScreen)) return;

        AutoSign autoSign = AutoSign.INSTANCE;
        if (autoSign == null) return;

        AbstractSignEditScreen signScreen = (AbstractSignEditScreen) screen;
        if (autoSign.queueAutoFill(((AbstractSignEditScreenAccessor) signScreen).collective$getBlockEntity().getPos())) {
            ci.cancel();
        }
    }
}
