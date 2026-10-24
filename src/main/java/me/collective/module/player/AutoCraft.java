package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ClickType;

/**
 * Порт AutoCraft из Meteor Client (рецепты 2x2 для инвентаря).
 *
 * <p>Крафтит ракеты, факелы, палки и верстаки на ходу.
 */
public class AutoCraft extends Module {
    public final ModeSetting recipe = add(new ModeSetting("Что", "Фейерверки", "Фейерверки", "Факелы", "Палки", "Верстак"));
    public final NumberSetting amount = add(new NumberSetting("Количество", 16.0, 1.0, 64.0, 1.0));
    public final NumberSetting delay = add(new NumberSetting("Задержка", 3.0, 0.0, 20.0, 1.0));
    public final BooleanSetting autoDisable = add(new BooleanSetting("Выкл когда готово", true));

    private int delayTimer = 0;
    private int crafted = 0;

    public AutoCraft() {
        super("AutoCraft", "Авто-крафт в сетке 2x2.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        crafted = 0;
        delayTimer = 0;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;
        if (mc.currentScreen != null) return;
        // Занятый курсор может затереть предметы — не крафтим с ним.
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        if (crafted >= amount.getIntValue()) {
            if (autoDisable.getValue() && isToggled()) setToggled(false);
            return;
        }

        boolean ok = switch (recipe.compute().trim()) {
            case "Факелы" -> craftTorch();
            case "Палки" -> craftSticks();
            case "Верстак" -> craftTable();
            default -> craftFirework();
        };
        if (ok) {
            crafted++;
            delayTimer = delay.getIntValue();
        } else if (autoDisable.getValue()) {
            warning("Нет материалов для крафта!");
            setToggled(false);
        }
    }

    private boolean craftFirework() {
        int paper = findInMain(Items.PAPER);
        int powder = findInMain(Items.GUNPOWDER);
        if (paper == -1 || powder == -1) return false;
        putOne(paper, 1);
        putOne(powder, 2);
        return takeResult();
    }

    private boolean craftTorch() {
        int coal = findInMain(Items.COAL, Items.CHARCOAL);
        int stick = findInMain(Items.STICK);
        if (coal == -1 || stick == -1) return false;
        putOne(coal, 1);
        putOne(stick, 3);
        return takeResult();
    }

    private boolean craftSticks() {
        int planks = findPlanks();
        if (planks == -1) return false;
        putOne(planks, 1);
        // второй слот той же доски
        int planks2 = findPlanks();
        if (planks2 == -1) {
            clearGrid();
            return false;
        }
        putOne(planks2, 3);
        return takeResult();
    }

    private boolean craftTable() {
        int[] slots = new int[4];
        for (int i = 0; i < 4; i++) {
            slots[i] = findPlanks();
            if (slots[i] == -1) {
                clearGrid();
                return false;
            }
            putOne(slots[i], i + 1);
        }
        return takeResult();
    }

    private int findInMain(Item... items) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            for (Item it : items) {
                if (s.isOf(it) && s.getCount() > 0) return i;
            }
        }
        return -1;
    }

    private int findPlanks() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.isIn(ItemTags.PLANKS) && s.getCount() > 0) return i;
        }
        return -1;
    }

    /** Кладёт 1 предмет из инвентаря в клетку сетки (1-4). */
    private void putOne(int invSlot, int craftSlot) {
        int sync = mc.player.currentScreenHandler.syncId;
        // Хотбарные слоты в PlayerScreenHandler идут ПОСЛЕ основного
        // инвентаря: инв. индекс 0-8 = хендлер 36-44, 9-35 = 9-35.
        int handlerSlot = invSlot < 9 ? 36 + invSlot : invSlot;
        mc.interactionManager.clickSlot(sync, handlerSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, craftSlot, 1, SlotActionType.PICKUP, mc.player);
        // остаток — обратно
        mc.interactionManager.clickSlot(sync, handlerSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void clearGrid() {
        int sync = mc.player.currentScreenHandler.syncId;
        for (int c = 1; c <= 4; c++) {
            if (mc.player.currentScreenHandler.getSlot(c).getStack().isEmpty()) continue;
            // Shift-клик возвращает крафт-слот прямо в инвентарь: курсор не
            // трогаем, иначе либо застрянет, либо затрёт поднятый предмет.
            mc.interactionManager.clickSlot(sync, c, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    private boolean takeResult() {
        int sync = mc.player.currentScreenHandler.syncId;
        ItemStack result = mc.player.currentScreenHandler.getSlot(0).getStack();
        if (result.isEmpty()) return false;
        mc.interactionManager.clickSlot(sync, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
        return true;
    }
}