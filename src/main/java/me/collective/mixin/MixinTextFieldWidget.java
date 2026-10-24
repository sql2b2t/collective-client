package me.collective.mixin;

import me.collective.font.FontManager;
import me.collective.module.ModuleManager;
import me.collective.module.client.Fonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Шрифт Fonts для строки ввода (чат, команды, поиск).
 *
 * <p>Подменяет отрисовку и измерение ширины, чтобы курсор совпадал
 * с кастомными глифами.
 */
@Mixin(TextFieldWidget.class)
public abstract class MixinTextFieldWidget {

    private static long lastLog = 0L;

    private static boolean useCustomFont() {
        Fonts fonts = ModuleManager.INSTANCE.getModule(Fonts.class);
        boolean on = fonts != null && fonts.isToggled() && fonts.chat.getValue();
        long now = System.currentTimeMillis();
        if (now - lastLog > 5000) {
            lastLog = now;
            System.out.println("[TextField-Debug] useCustom=" + on);
        }
        return on;
    }

    @Redirect(
        method = "renderWidget",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawTextWithShadow"
                + "(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Component;III)V")
    )
    private void drawCustomInputText(GuiGraphics context, TextRenderer renderer, Component text, int x, int y, int color) {
        if (useCustomFont()) {
            FontManager.INSTANCE.drawStringWithShadow(context, text.getString(), x, y, color);
        } else {
            context.drawTextWithShadow(renderer, text, x, y, color);
        }
    }

    @Redirect(
        method = "renderWidget",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Ljava/lang/String;)I")
    )
    private int customInputWidth(TextRenderer renderer, String text) {
        if (useCustomFont()) {
            return (int) FontManager.INSTANCE.getStringWidth(text);
        }
        return renderer.getWidth(text);
    }
}