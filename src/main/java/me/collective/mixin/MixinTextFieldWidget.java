package me.collective.mixin;

import me.collective.font.FontManager;
import me.collective.module.ModuleManager;
import me.collective.module.client.Fonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Шрифт Fonts для строки ввода (чат, команды, поиск).
 *
 * <p>Подменяет отрисовку и измерение ширины, чтобы курсор совпадал
 * с кастомными глифами.
 */
@Mixin(TextFieldWidget.class)
public abstract class MixinTextFieldWidget {

    private static final Logger LOG = LoggerFactory.getLogger(MixinTextFieldWidget.class);

    private static long lastLog = 0L;

    private static boolean useCustomFont() {
        Fonts fonts = ModuleManager.INSTANCE.getModule(Fonts.class);
        boolean on = fonts != null && fonts.isToggled() && fonts.chat.getValue();
        long now = System.currentTimeMillis();
        if (now - lastLog > 5000) {
            lastLog = now;
            // Heartbeat-диагностика: в stdout она спамила каждые 5 секунд,
            // на уровне debug видна только при явной диагностике.
            LOG.debug("[TextField-Debug] useCustom={}", on);
        }
        return on;
    }

    @Redirect(
        method = "renderWidget",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow"
                + "(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V")
    )
    private void drawCustomInputText(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color) {
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
