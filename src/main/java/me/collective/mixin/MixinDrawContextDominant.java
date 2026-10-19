package me.collective.mixin;

import me.collective.module.render.Tooltips;
import me.collective.module.tooltips.DominantStacks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подмена иконки шилкера доминирующим предметом — порт
 * {@code MixinDrawContextDominant} из b2xy.
 *
 * <p>Внедряется в отрисовку счётчика слота ({@code drawStackOverlay}) и, если
 * предмет совпадает с записью из накопленного списка, рисует поверх иконку
 * доминирующего предмета с тем же искажением, что в оригинале: сдвиг
 * {@code +3.5 / -4} и масштаб {@code 10 / -10} (отрицательный Y зеркалит
 * иконку). Смещение и масштаб заданы явно через матрицы {@code DrawContext},
 * т.к. в 1.21.11 {@code drawItem} уходит в render-состояния и хуков на
 * {@code translate}/{@code scale} внутри может не оказаться.
 */
@Mixin(DrawContext.class)
public abstract class MixinDrawContextDominant {

    /** Модуль кэшируется один раз: drawStackOverlay вызывается на каждый отрисованный предмет. */
    private Tooltips collective$module;

    @Inject(
        method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;"
            + "Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
        at = @At("HEAD"))
    private void collective$majorityItem(TextRenderer font, ItemStack stack, int x, int y,
                                   String countLabel, CallbackInfo ci) {
        Tooltips module = this.collective$module;
        if (module == null) {
            this.collective$module = module = Tooltips.INSTANCE;
            if (module == null) return; // модули ещё не зарегистрированы
        }

        if (!module.majorityActive()) return;
        if (module.dominantEntries().isEmpty()) return;

        for (DominantStacks.Entry entry : module.dominantEntries()) {
            if (entry.container() != stack || entry.dominant() == null) continue;
            if (entry.dominant().isEmpty()) continue;

            drawDistorted(entry.dominant(), x, y);
            return;
        }
    }

    /** Иконка доминирующего предмета с искажением оригинала. */
    private void drawDistorted(ItemStack stack, int x, int y) {
        DrawContext context = (DrawContext) (Object) this;

        var matrices = context.getMatrices();

        matrices.pushMatrix();
        // тот же сдвиг, что давали хуки на translate в оригинале
        matrices.translate(x + 3.5f, y - 4.0f);
        // тот же масштаб; отрицательный Y зеркалит иконку по вертикали
        matrices.scale(10.0f, -10.0f);

        context.drawItem(stack, 0, 0);

        matrices.popMatrix();
    }
}
