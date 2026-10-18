package me.collective.mixin;

import me.collective.module.player.ItemScroller;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Прокрутка колеса над слотами контейнеров для модуля ItemScroller. */
@Mixin(HandledScreen.class)
public abstract class MixinHandledScreenScroll {

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> info) {
        ItemScroller module = ItemScroller.INSTANCE;
        if (module == null || !module.isToggled()) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        Slot hovered = null;
        for (Slot slot : screen.getScreenHandler().slots) {
            double slotX = this.x + slot.x;
            double slotY = this.y + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16.0 && mouseY >= slotY && mouseY < slotY + 16.0) {
                hovered = slot;
                break;
            }
        }
        if (hovered == null) return;

        if (module.onScroll(screen, hovered, verticalAmount)) {
            info.setReturnValue(true);
            info.cancel();
        }
    }
}
