package me.collective.mixin;

import me.collective.gui.alt.AltManagerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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
        ((ScreenAccessor) self).invokeAddDrawableChild(ButtonWidget.builder(Text.literal("Alts"), button ->
            MinecraftClient.getInstance().setScreen(new AltManagerScreen(self))
        ).dimensions(self.width - 108, self.height - 28, 100, 20).build());
    }
}
