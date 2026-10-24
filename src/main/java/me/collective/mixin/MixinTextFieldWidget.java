package me.collective.mixin;

import me.collective.font.ChatFontUtil;
import me.collective.font.FontManager;
import me.collective.module.ModuleManager;
import me.collective.module.client.Fonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Шрифт Fonts для строки ввода (чат, команды, поиск).
 *
 * <p>Подменяет отрисовку и измерение ширины, чтобы курсор совпадал
 * с кастомными глифами.
 */
@Mixin(EditBox.class)
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
        method = "extractWidgetRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V")
    )
    private void drawCustomInputText(GuiGraphicsExtractor context, Font renderer, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (useCustomFont()) {
            FontManager.INSTANCE.drawStringWithShadow(context, ChatFontUtil.toSectionString(text), x, y, color);
        } else {
            context.text(renderer, text, x, y, color, shadow);
        }
    }

    @Redirect(
        method = "extractWidgetRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I")
    )
    private int customInputWidth(Font renderer, String text) {
        if (useCustomFont()) {
            return (int) FontManager.INSTANCE.getStringWidth(text);
        }
        return renderer.width(text);
    }
}