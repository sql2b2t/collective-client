package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.Click;
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
 * Расширенное управление предметами в инвентарях и сундуках (ItemScroller как в Meteor Client):
 * - Shift + ведение мышью: быстрый перенос всех слотов, по которым проводится курсор
 * - Ctrl + Shift + клик / ведение: перенос всех предметов одного типа в целевой контейнер
 * - Q + ведение мышью: быстрый выброс предметов из слотов
 * - Прокрутка колесом мыши: вниз — отдать в контейнер, вверх — забрать из контейнера
 */
public class ItemScroller extends Module {
    public static ItemScroller INSTANCE;

    public final BooleanSetting mouseDrag = add(new BooleanSetting("MouseDrag", true));
    public final BooleanSetting shiftDrag = add(new BooleanSetting("ShiftDrag", true, (v) -> mouseDrag.getValue()));
    public final BooleanSetting ctrlShiftAll = add(new BooleanSetting("CtrlShiftAll", true));
    public final BooleanSetting dropDrag = add(new BooleanSetting("DropDrag", true, (v) -> mouseDrag.getValue()));
    public final BooleanSetting scroll = add(new BooleanSetting("Scroll", true));
    public final BooleanSetting scrollDirection = add(new BooleanSetting("Directional", true, (v) -> scroll.getValue()));
    public final NumberSetting stackLimit = add(new NumberSetting("StackLimit", 36, 1, 36, 1));

    private int lastDraggedSlotId = -1;

    public ItemScroller() {
        super("ItemScroller", "Удобное перемещение предметов: ведение мышью с Shift (как в Meteor), Ctrl+Shift перенос всех стаков и прокрутка колесом.", Category.PLAYER);
        INSTANCE = this;
    }

    /**
     * Обрабатывает ведение курсора с зажатой кнопкой мыши по слотам контейнера (MouseDrag).
     */
    public boolean onMouseDrag(HandledScreen<?> screen, Slot hovered, Click click) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!mouseDrag.getValue()) return false;
        if (hovered == null) return false;
        if (hovered.id == lastDraggedSlotId) return false;

        ScreenHandler handler = screen.getScreenHandler();
        if (handler != mc.player.currentScreenHandler) return false;

        // Выбрасывание предметов при зажатой клавише Q
        if (dropDrag.getValue() && isKeyDown(GLFW.GLFW_KEY_Q)) {
            if (!hovered.hasStack() || !hovered.canTakeItems(mc.player)) return false;
            lastDraggedSlotId = hovered.id;
            boolean dropAll = isCtrlDown();
            mc.interactionManager.clickSlot(handler.syncId, hovered.id, dropAll ? 1 : 0, SlotActionType.THROW, mc.player);
            return true;
        }

        // Для перемещения предметов обрабатываем только левую кнопку мыши (button 0)
        if (click.button() != 0) return false;

        // Если на курсоре уже зажат предмет, не выполняем автоматическое перемещение
        if (!handler.getCursorStack().isEmpty()) return false;

        // Ctrl + Shift + Drag: перенос всех предметов совпадающего типа
        if (ctrlShiftAll.getValue() && isShiftDown() && isCtrlDown()) {
            if (!hovered.hasStack() || !hovered.canTakeItems(mc.player)) return false;
            lastDraggedSlotId = hovered.id;
            boolean playerSide = hovered.inventory instanceof PlayerInventory;
            return moveMatching(handler, hovered, playerSide);
        }

        // Shift + Drag: быстрый перенос каждого слота по траектории курсора (как в Meteor)
        if (shiftDrag.getValue() && isShiftDown()) {
            if (!hovered.hasStack() || !hovered.canTakeItems(mc.player)) return false;
            lastDraggedSlotId = hovered.id;
            mc.interactionManager.clickSlot(handler.syncId, hovered.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            return true;
        }

        return false;
    }

    /**
     * Обрабатывает одиночный клик по слоту (для комбинаций Ctrl + Shift).
     */
    public boolean onMouseClick(HandledScreen<?> screen, Slot hovered, Click click) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (hovered == null || !hovered.hasStack()) return false;

        ScreenHandler handler = screen.getScreenHandler();
        if (handler != mc.player.currentScreenHandler) return false;

        if (click.button() == 0 && ctrlShiftAll.getValue() && isShiftDown() && isCtrlDown()) {
            if (!hovered.canTakeItems(mc.player)) return false;
            lastDraggedSlotId = hovered.id;
            boolean playerSide = hovered.inventory instanceof PlayerInventory;
            return moveMatching(handler, hovered, playerSide);
        }

        return false;
    }

    /**
     * Сброс ID последнего слота при отпускании кнопки мыши.
     */
    public void onMouseRelease(int button) {
        lastDraggedSlotId = -1;
    }

    /**
     * Обрабатывает прокрутку колеса мыши над слотом.
     */
    public boolean onScroll(HandledScreen<?> screen, Slot hovered, double amount) {
        if (!scroll.getValue() || amount == 0 || mc.player == null || mc.interactionManager == null) return false;

        ScreenHandler handler = screen.getScreenHandler();
        if (handler != mc.player.currentScreenHandler) return false;
        if (!handler.getCursorStack().isEmpty()) return false;

        boolean playerSide = hovered.inventory instanceof PlayerInventory;
        boolean push = !scrollDirection.getValue() || amount < 0;

        if (push) {
            // Прокрутка вниз: отдаём из hovered-слота в противоположный инвентарь
            ItemStack source = hovered.getStack();
            if (source.isEmpty() || !hovered.canTakeItems(mc.player)) return false;

            if (isCtrlDown()) {
                return moveMatching(handler, hovered, playerSide);
            }
            if (isShiftDown()) {
                mc.interactionManager.clickSlot(handler.syncId, hovered.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
            return moveSingle(handler, hovered, playerSide);
        } else {
            // Прокрутка вверх: забираем из противоположного инвентаря в hovered-слот
            if (isCtrlDown()) {
                ItemStack sample = hovered.getStack();
                if (sample.isEmpty()) return false;
                return moveMatching(handler, hovered, !playerSide);
            }
            if (isShiftDown()) {
                ItemStack sample = hovered.getStack();
                if (sample.isEmpty()) return false;
                Slot otherSlot = findMatchingSlot(handler, !playerSide, sample);
                if (otherSlot != null) {
                    mc.interactionManager.clickSlot(handler.syncId, otherSlot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
                return false;
            }
            return pullSingle(handler, hovered, playerSide);
        }
    }

    private boolean moveSingle(ScreenHandler handler, Slot source, boolean playerSide) {
        Slot dest = findDestination(handler, playerSide, source.getStack());
        if (dest == null) return false;
        click(handler, source, 0);
        click(handler, dest, 1);
        click(handler, source, 0);
        return true;
    }

    private boolean pullSingle(ScreenHandler handler, Slot hovered, boolean playerSide) {
        Slot otherSlot;
        if (hovered.hasStack()) {
            otherSlot = findMatchingSlot(handler, !playerSide, hovered.getStack());
        } else {
            otherSlot = findFirstAvailableSlot(handler, !playerSide);
        }
        if (otherSlot == null || !otherSlot.canTakeItems(mc.player)) return false;

        click(handler, otherSlot, 0);
        click(handler, hovered, 1);
        click(handler, otherSlot, 0);
        return true;
    }

    private boolean moveMatching(ScreenHandler handler, Slot hovered, boolean playerSide) {
        if (hovered == null || !hovered.hasStack()) return false;
        Item item = hovered.getStack().getItem();
        int limit = stackLimit.getIntValue();
        int moved = 0;
        for (Slot slot : new ArrayList<>(handler.slots)) {
            if (moved >= limit) break;
            if ((slot.inventory instanceof PlayerInventory) != playerSide) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !stack.isOf(item) || !slot.canTakeItems(mc.player)) continue;
            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            moved++;
        }
        return moved > 0;
    }

    private Slot findMatchingSlot(ScreenHandler handler, boolean playerSide, ItemStack sample) {
        for (Slot slot : handler.slots) {
            if ((slot.inventory instanceof PlayerInventory) != playerSide) continue;
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, sample) && slot.canTakeItems(mc.player)) {
                return slot;
            }
        }
        return null;
    }

    private Slot findFirstAvailableSlot(ScreenHandler handler, boolean playerSide) {
        for (Slot slot : handler.slots) {
            if ((slot.inventory instanceof PlayerInventory) != playerSide) continue;
            if (slot.hasStack() && slot.canTakeItems(mc.player)) {
                return slot;
            }
        }
        return null;
    }

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

    private boolean isShiftDown() {
        if (mc == null || mc.getWindow() == null) return false;
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean isCtrlDown() {
        if (mc == null || mc.getWindow() == null) return false;
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean isKeyDown(int key) {
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }
}
