package me.collective.mixin;

import me.collective.gui.alt.AltManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Кнопка Alt Manager в главном меню. */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen {

    @Inject(method = "init", at = @At("TAIL"))
    private void addAltsButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        ((ScreenAccessor) self).invokeAddDrawableChild(ButtonWidget.builder(Component.literal("Alts"), button ->
            Minecraft.getInstance().setScreen(new AltManagerScreen(self))
        ).dimensions(self.width - 108, self.height - 28, 100, 20).build());
    }
}