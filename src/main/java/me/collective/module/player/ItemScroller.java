package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

/**
 * Переносит предметы между инвентарями прокруткой колеса над слотом.
 *
 * <p>Наведённый слот — источник: колесо переносит один предмет, Shift+колесо — весь стак,
 * Ctrl+колесо — все стаки этого предмета (лимит настраивается). Перенос выполняется
 * последовательностью обычных кликов по слотам, поэтому состояние контейнера остаётся
 * синхронным с сервером.
 */
public class ItemScroller extends Module {
    public static ItemScroller INSTANCE;

    public final NumberSetting stackLimit = add(new NumberSetting("Стаков за прокрутку", 12, 1, 36, 1));

    public ItemScroller() {
        super("ItemScroller", "Прокрутка колесом над слотом переносит предметы между инвентарями: колесо — один предмет, Shift — весь стак, Ctrl — все стаки этого предмета.", Category.PLAYER);
        INSTANCE = this;
    }

    /** Обрабатывает прокрутку над слотом. Возвращает true, если прокрутка использована. */
    public boolean onScroll(HandledScreen<?> screen, Slot hovered, double amount) {
        if (amount == 0 || mc.player == null || mc.interactionManager == null) return false;

        ScreenHandler handler = screen.getScreenHandler();
        if (handler != mc.player.currentScreenHandler) return false;
        if (!handler.getCursorStack().isEmpty()) return false;

        ItemStack source = hovered.getStack();
        if (source.isEmpty() || !hovered.canTakeItems(mc.player) || !hovered.canInsert(source)) return false;

        boolean playerSide = hovered.inventory instanceof PlayerInventory;
        if (isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            return moveMatching(handler, hovered, playerSide);
        }
        if (isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            return moveStack(handler, hovered, playerSide);
        }
        return moveSingle(handler, hovered, playerSide);
    }

    private boolean moveSingle(ScreenHandler handler, Slot source, boolean playerSide) {
        Slot dest = findDestination(handler, playerSide, source.getStack());
        if (dest == null) return false;
        move(handler, source, dest, true);
        return true;
    }

    private boolean moveStack(ScreenHandler handler, Slot source, boolean playerSide) {
        Slot dest = findDestination(handler, playerSide, source.getStack());
        if (dest == null) return false;
        move(handler, source, dest, false);
        return true;
    }

    private boolean moveMatching(ScreenHandler handler, Slot hovered, boolean playerSide) {
        Item item = hovered.getStack().getItem();
        int limit = stackLimit.getIntValue();
        int moved = 0;
        for (Slot slot : new ArrayList<>(handler.slots)) {
            if (moved >= limit) break;
            if ((slot.inventory instanceof PlayerInventory) != playerSide) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !stack.isOf(item) || !slot.canTakeItems(mc.player) || !slot.canInsert(stack)) continue;
            Slot dest = findDestination(handler, playerSide, stack);
            if (dest == null) continue;
            move(handler, slot, dest, false);
            moved++;
        }
        return moved > 0;
    }

    /**
     * Переносит стак из источника в приёмник тремя кликами: поднять, положить
     * (по одному предмету при single или весь стак) и вернуть остаток обратно.
     */
    private void move(ScreenHandler handler, Slot source, Slot dest, boolean single) {
        click(handler, source, 0);
        click(handler, dest, single ? 1 : 0);
        click(handler, source, 0);
    }

    /** Ищет ближайший подходящий слот на противоположной стороне: сначала частичный стак, затем пустой. */
    private Slot findDestination(ScreenHandler handler, boolean sourcePlayerSide, ItemStack sample) {
        Slot mergeable = null;
        Slot empty = null;
        for (Slot slot : handler.slots) {
            if ((slot.inventory instanceof PlayerInventory) == sourcePlayerSide) continue;
            if (!slot.canInsert(sample)) continue;
            ItemStack inSlot = slot.getStack();
            if (inSlot.isEmpty()) {
                if (empty == null) empty = slot;
                continue;
            }
            if (mergeable == null
                    && ItemStack.areItemsAndComponentsEqual(inSlot, sample)
                    && inSlot.getCount() < slot.getMaxItemCount(sample)) {
                mergeable = slot;
            }
        }
        return mergeable != null ? mergeable : empty;
    }

    private void click(ScreenHandler handler, Slot slot, int button) {
        mc.interactionManager.clickSlot(handler.syncId, slot.id, button, SlotActionType.PICKUP, mc.player);
    }

    private boolean isKeyDown(int key) {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }
}
