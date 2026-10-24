package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
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
    public boolean onMouseDrag(AbstractContainerScreen<?> screen, Slot hovered, net.minecraft.client.input.MouseButtonEvent click) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (!mouseDrag.getValue()) return false;
        if (hovered == null) return false;
        if (hovered.index == lastDraggedSlotId) return false;

        AbstractContainerMenu handler = screen.getMenu();
        if (handler != mc.player.containerMenu) return false;

        // Выбрасывание предметов при зажатой клавише Q
        if (dropDrag.getValue() && isKeyDown(GLFW.GLFW_KEY_Q)) {
            if (!hovered.hasItem() || !hovered.mayPickup(mc.player)) return false;
            lastDraggedSlotId = hovered.index;
            boolean dropAll = isCtrlDown();
            mc.gameMode.handleContainerInput(handler.containerId, hovered.index, dropAll ? 1 : 0, ContainerInput.THROW, mc.player);
            return true;
        }

        // Для перемещения предметов обрабатываем только левую кнопку мыши (button 0)
        if (me.collective.util.KeyHelper.toStandardMouseButton(click.button()) != 0) return false;

        // Если на курсоре уже зажат предмет, не выполняем автоматическое перемещение
        if (!handler.getCarried().isEmpty()) return false;

        // Ctrl + Shift + Drag: перенос всех предметов совпадающего типа
        if (ctrlShiftAll.getValue() && isShiftDown() && isCtrlDown()) {
            if (!hovered.hasItem() || !hovered.mayPickup(mc.player)) return false;
            lastDraggedSlotId = hovered.index;
            boolean playerSide = hovered.container instanceof Inventory;
            return moveMatching(handler, hovered, playerSide);
        }

        // Shift + Drag: быстрый перенос каждого слота по траектории курсора (как в Meteor)
        if (shiftDrag.getValue() && isShiftDown()) {
            if (!hovered.hasItem() || !hovered.mayPickup(mc.player)) return false;
            lastDraggedSlotId = hovered.index;
            mc.gameMode.handleContainerInput(handler.containerId, hovered.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            return true;
        }

        return false;
    }

    /**
     * Обрабатывает одиночный клик по слоту (для комбинаций Ctrl + Shift).
     */
    public boolean onMouseClick(AbstractContainerScreen<?> screen, Slot hovered, net.minecraft.client.input.MouseButtonEvent click) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (hovered == null || !hovered.hasItem()) return false;

        AbstractContainerMenu handler = screen.getMenu();
        if (handler != mc.player.containerMenu) return false;

        if (me.collective.util.KeyHelper.toStandardMouseButton(click.button()) == 0 && ctrlShiftAll.getValue() && isShiftDown() && isCtrlDown()) {
            if (!hovered.mayPickup(mc.player)) return false;
            lastDraggedSlotId = hovered.index;
            boolean playerSide = hovered.container instanceof Inventory;
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
    public boolean onScroll(AbstractContainerScreen<?> screen, Slot hovered, double amount) {
        if (!scroll.getValue() || amount == 0 || mc.player == null || mc.gameMode == null) return false;

        AbstractContainerMenu handler = screen.getMenu();
        if (handler != mc.player.containerMenu) return false;
        if (!handler.getCarried().isEmpty()) return false;

        boolean playerSide = hovered.container instanceof Inventory;
        boolean push = !scrollDirection.getValue() || amount < 0;

        if (push) {
            // Прокрутка вниз: отдаём из hovered-слота в противоположный инвентарь
            ItemStack source = hovered.getItem();
            if (source.isEmpty() || !hovered.mayPickup(mc.player)) return false;

            if (isCtrlDown()) {
                return moveMatching(handler, hovered, playerSide);
            }
            if (isShiftDown()) {
                mc.gameMode.handleContainerInput(handler.containerId, hovered.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                return true;
            }
            return moveSingle(handler, hovered, playerSide);
        } else {
            // Прокрутка вверх: забираем из противоположного инвентаря в hovered-слот
            if (isCtrlDown()) {
                ItemStack sample = hovered.getItem();
                if (sample.isEmpty()) return false;
                return moveMatching(handler, hovered, !playerSide);
            }
            if (isShiftDown()) {
                ItemStack sample = hovered.getItem();
                if (sample.isEmpty()) return false;
                Slot otherSlot = findMatchingSlot(handler, !playerSide, sample);
                if (otherSlot != null) {
                    mc.gameMode.handleContainerInput(handler.containerId, otherSlot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                    return true;
                }
                return false;
            }
            return pullSingle(handler, hovered, playerSide);
        }
    }

    private boolean moveSingle(AbstractContainerMenu handler, Slot source, boolean playerSide) {
        Slot dest = findDestination(handler, playerSide, source.getItem());
        if (dest == null) return false;
        click(handler, source, 0);
        click(handler, dest, 1);
        click(handler, source, 0);
        return true;
    }

    private boolean pullSingle(AbstractContainerMenu handler, Slot hovered, boolean playerSide) {
        Slot otherSlot;
        if (hovered.hasItem()) {
            otherSlot = findMatchingSlot(handler, !playerSide, hovered.getItem());
        } else {
            otherSlot = findFirstAvailableSlot(handler, !playerSide);
        }
        if (otherSlot == null || !otherSlot.mayPickup(mc.player)) return false;

        click(handler, otherSlot, 0);
        click(handler, hovered, 1);
        click(handler, otherSlot, 0);
        return true;
    }

    private boolean moveMatching(AbstractContainerMenu handler, Slot hovered, boolean playerSide) {
        if (hovered == null || !hovered.hasItem()) return false;
        Item item = hovered.getItem().getItem();
        int limit = stackLimit.getIntValue();
        int moved = 0;
        for (Slot slot : new ArrayList<>(handler.slots)) {
            if (moved >= limit) break;
            if ((slot.container instanceof Inventory) != playerSide) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(item) || !slot.mayPickup(mc.player)) continue;
            mc.gameMode.handleContainerInput(handler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            moved++;
        }
        return moved > 0;
    }

    private Slot findMatchingSlot(AbstractContainerMenu handler, boolean playerSide, ItemStack sample) {
        for (Slot slot : handler.slots) {
            if ((slot.container instanceof Inventory) != playerSide) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample) && slot.mayPickup(mc.player)) {
                return slot;
            }
        }
        return null;
    }

    private Slot findFirstAvailableSlot(AbstractContainerMenu handler, boolean playerSide) {
        for (Slot slot : handler.slots) {
            if ((slot.container instanceof Inventory) != playerSide) continue;
            if (slot.hasItem() && slot.mayPickup(mc.player)) {
                return slot;
            }
        }
        return null;
    }

    private Slot findDestination(AbstractContainerMenu handler, boolean sourcePlayerSide, ItemStack sample) {
        Slot mergeable = null;
        Slot empty = null;
        for (Slot slot : handler.slots) {
            if ((slot.container instanceof Inventory) == sourcePlayerSide) continue;
            if (!slot.mayPlace(sample)) continue;
            ItemStack inSlot = slot.getItem();
            if (inSlot.isEmpty()) {
                if (empty == null) empty = slot;
                continue;
            }
            if (mergeable == null
                    && ItemStack.isSameItemSameComponents(inSlot, sample)
                    && inSlot.getCount() < slot.getMaxStackSize(sample)) {
                mergeable = slot;
            }
        }
        return mergeable != null ? mergeable : empty;
    }

    private void click(AbstractContainerMenu handler, Slot slot, int button) {
        mc.gameMode.handleContainerInput(handler.containerId, slot.index, button, ContainerInput.PICKUP, mc.player);
    }

    private boolean isShiftDown() {
        return me.collective.util.KeyHelper.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || me.collective.util.KeyHelper.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
    }

    private boolean isCtrlDown() {
        return me.collective.util.KeyHelper.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || me.collective.util.KeyHelper.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
    }

    private boolean isKeyDown(int key) {
        return me.collective.util.KeyHelper.isKeyDown(key);
    }
}