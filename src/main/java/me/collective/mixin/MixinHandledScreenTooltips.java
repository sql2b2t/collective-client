package me.collective.mixin;

import me.collective.module.ModuleManager;
import me.collective.module.render.Tooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Optional;

/**
 * Подмена отрисовки тултипа — порт {@code MixinHandledScreenTooltips} из b2xy.
 *
 * <p>Перехватывает {@code GuiGraphics.drawTooltip} в
 * {@code HandledScreen.drawMouseoverTooltip} через {@code @Redirect}, собирает
 * из строк и {@code TooltipData} список компонентов через
 * {@link Tooltips#buildComponents} и рисует результат штатным
 * {@code drawTooltipImmediately}. Содержимое, порядок, размеры и
 * позиционирование совпадают с ванильными.
 *
 * <p>Цветная рамка рисуется вручную вокруг содержимого (в 1.21.11 рамка —
 * текстура со {@code static final} цветами, подкрасить её из кода нельзя).
 */
@Mixin(HandledScreen.class)
public abstract class MixinHandledScreenTooltips {

    /** Отступы рамки тултипа в 1.21.11: 1 пиксель бордер плюс 3 пикселя поля. */
    private static final int BORDER = 1;
    private static final int PADDING = 3;

    @Shadow
    protected Slot focusedSlot;

    @Redirect(
        method = "drawMouseoverTooltip",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawTooltip"
                + "(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II"
                + "Lnet/minecraft/util/Identifier;)V")
    )
    private void collective$tooltips(GuiGraphics context, TextRenderer font, List<Component> lines,
                                Optional<TooltipData> data, int mouseX, int mouseY,
                                Identifier background) {
        Tooltips module = ModuleManager.INSTANCE.getModule(Tooltips.class);
        if (module == null || !module.isActive()) {
            context.drawTooltip(font, lines, data, mouseX, mouseY, background);
            return;
        }

        ItemStack stack = focusedSlot != null ? focusedSlot.getStack() : ItemStack.EMPTY;

        List<TooltipComponent> components =
            module.buildComponents(lines, data.orElse(null), stack, mouseX, mouseY);

        Integer border = module.borderColor();

        if (border == null) {
            context.drawTooltipImmediately(font, components, mouseX, mouseY,
                HoveredTooltipPositioner.INSTANCE, background);
            return;
        }

        // рамка рисуется вручную, содержимое — на прежнем месте
        int width = contentWidth(font, components);
        int height = contentHeight(font, components);

        var position = HoveredTooltipPositioner.INSTANCE.getPosition(
            context.getScaledWindowWidth(), context.getScaledWindowHeight(),
            mouseX, mouseY, width, height);

        int x = position.x() - PADDING;
        int y = position.y() - PADDING;
        int frameWidth = width + PADDING * 2;
        int frameHeight = height + PADDING * 2;

        context.fill(x, y, x + frameWidth, y + frameHeight, border);

        context.drawTooltipImmediately(font, components, mouseX, mouseY,
            HoveredTooltipPositioner.INSTANCE, background);
    }

    /** Ширина содержимого: как у ванильного тултипа, но без рамки. */
    private static int contentWidth(TextRenderer font, List<TooltipComponent> components) {
        int width = 0;
        for (TooltipComponent component : components) {
            width = Math.max(width, component.getWidth(font));
        }
        return width + BORDER * 2;
    }

    private static int contentHeight(TextRenderer font, List<TooltipComponent> components) {
        int height = 0;
        for (TooltipComponent component : components) {
            height += component.getHeight(font);
        }
        return height + BORDER * 2;
    }
}