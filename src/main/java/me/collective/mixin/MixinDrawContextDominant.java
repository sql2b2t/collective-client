package me.collective.mixin;

import me.collective.module.render.Tooltips;
import me.collective.module.tooltips.DominantStacks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
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
 * иконку). Смещение и масштаб заданы явно через матрицы {@code GuiGraphicsExtractor},
 * т.к. в 1.21.11 {@code drawItem} уходит в render-состояния и хуков на
 * {@code translate}/{@code scale} внутри может не оказаться.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinDrawContextDominant {

    /** Модуль кэшируется один раз: drawStackOverlay вызывается на каждый отрисованный предмет. */
    private Tooltips collective$module;

    @Inject(
        method = "itemDecorations(Lnet/minecraft/client/gui/Font;"
            + "Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
        at = @At("HEAD"))
    private void collective$majorityItem(Font font, ItemStack stack, int x, int y,
                                   String countLabel, CallbackInfo ci) {
        Tooltips module = this.collective$module;
        if (module == null) {
            this.collective$module = module = Tooltips.INSTANCE;
            if (module == null) return; // модули ещё не зарегистрированы
        }

        if (!module.majorityActive()) return;
        if (stack == null || stack.isEmpty()) return;

        ItemStack dominant = DominantStacks.getDominant(stack);
        if (dominant == null || dominant.isEmpty()) return;

        renderDominantItem(dominant, x, y, module.getMajorityMode(), module.getMajorityPosition());
    }

    /**
     * Отрисовывает доминирующий предмет поверх контейнера (шаposition/мешка).
     */
    private void renderDominantItem(ItemStack dominant, int x, int y, Tooltips.MajorityMode mode, Tooltips.MajorityPosition position) {
        GuiGraphicsExtractor context = (GuiGraphicsExtractor) (Object) this;

        if (mode == Tooltips.MajorityMode.FULL) {
            context.item(dominant, x, y);
        } else {
            // Миниатюра 10×10 (scale = 10/16 = 0.625)
            var matrices = context.pose();
            matrices.pushMatrix();

            if (position == Tooltips.MajorityPosition.CENTER) {
                // По центру слота
                matrices.translate(x + 3.0f, y + 3.0f);
            } else {
                // В правом верхнем углу слота
                matrices.translate(x + 6.0f, y + 0.5f);
            }

            matrices.scale(0.625f, 0.625f);
            context.item(dominant, 0, 0);
            matrices.popMatrix();
        }
    }
}