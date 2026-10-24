package me.collective.mixin;

import me.collective.module.player.ItemScroller;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Расширенное управление предметами в контейнерах для модуля ItemScroller (как в Meteor Client):
 * - Ведение мышью с Shift (быстрый перенос всех слотов по курсору)
 * - Ctrl + Shift клик / ведение (перенос всех стаков совпадающего типа)
 * - Ведение с клавишей Q (быстрый выброс предметов)
 * - Прокрутка колесом мыши (вниз — отдать, вверх — забрать)
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreenScroll {

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    private Slot getSlotUnderMouse(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen == null || screen.getMenu() == null) return null;
        for (Slot slot : screen.getMenu().slots) {
            double slotX = this.x + slot.x;
            double slotY = this.y + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16.0 && mouseY >= slotY && mouseY < slotY + 16.0) {
                return slot;
            }
        }
        return null;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> info) {
        ItemScroller module = ItemScroller.INSTANCE;
        if (module == null || !module.isToggled()) return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Slot hovered = getSlotUnderMouse(screen, mouseX, mouseY);
        if (hovered == null) return;

        if (module.onScroll(screen, hovered, verticalAmount)) {
            info.setReturnValue(true);
            info.cancel();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> info) {
        ItemScroller module = ItemScroller.INSTANCE;
        if (module == null || !module.isToggled()) return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Slot hovered = getSlotUnderMouse(screen, click.x(), click.y());
        if (module.onMouseDrag(screen, hovered, click)) {
            info.setReturnValue(true);
            info.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean bl, CallbackInfoReturnable<Boolean> info) {
        ItemScroller module = ItemScroller.INSTANCE;
        if (module == null || !module.isToggled()) return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Slot hovered = getSlotUnderMouse(screen, click.x(), click.y());
        if (hovered != null && module.onMouseClick(screen, hovered, click)) {
            info.setReturnValue(true);
            info.cancel();
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void onMouseReleased(net.minecraft.client.input.MouseButtonEvent click, CallbackInfoReturnable<Boolean> info) {
        ItemScroller module = ItemScroller.INSTANCE;
        if (module != null) {
            module.onMouseRelease(click.button());
        }
    }
}