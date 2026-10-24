package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.StringSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Порт HotbarManager: раскладывает предметы по слотам хотбара.
 * Слоты задаются id предметов через запятую (пусто — не трогать).
 */
public class HotbarManager extends Module {
    public final NumberSetting delay = add(new NumberSetting("Delay", 1, 1, 20, 1));
    public final BooleanSetting replace = add(new BooleanSetting("Replace", true));
    public final BooleanSetting toggle = add(new BooleanSetting("ToggleOff", false));

    private final List<StringSetting> slotSettings = new ArrayList<>();
    private int waitTicks;

    public HotbarManager() {
        super("HotbarManager", "Автоматически перемещает нужные предметы и блоки в хотбар.", Category.HIGHWAY);
        for (int i = 0; i < 9; i++) {
            slotSettings.add(add(new StringSetting("Slot" + (i + 1), i == 0 ? "minecraft:diamond_pickaxe" : "")));
        }
    }

    @Override
    public void onEnable() {
        waitTicks = 0;
        for (int i = 0; i < 9; i++) {
            Item item = parseItem(slotSettings.get(i).getValue());
            if (item == null && !slotSettings.get(i).getValue().strip().isEmpty()) {
                info("Slot" + (i + 1) + ": неизвестный предмет «" + slotSettings.get(i).getValue().strip() + "».");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        // Занятый курсор может затереть предметы — не раскладываем с ним.
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        int highestSlot = -1;
        for (int i = 0; i < 9; i++) {
            highestSlot = Math.max(highestSlot, i);
            Item want = parseItem(slotSettings.get(i).getValue());
            if (want == null || want == Items.AIR) continue;

            Item slotItem = mc.player.getInventory().getStack(i).getItem();
            if (slotItem != Items.AIR && !replace.getValue()) continue;
            if (mc.player.getInventory().getStack(i).isOf(want)) continue;

            FindItemResult result = InventoryUtil.find(stack -> stack.isOf(want), i, 35);
            if (!result.found()) continue;

            moveClick(result.slot(), i);
            waitTicks = delay.getIntValue();
            return;
        }

        if (highestSlot == 8 && toggle.getValue()) setToggled(false);
    }

    /** Безопасный SWAP в 1 пакет: идентичен нажатию хоткей-цифры в инвентаре. */
    private void moveClick(int fromInv, int toHotbar) {
        int from = fromInv < 9 ? 36 + fromInv : fromInv;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, from, toHotbar, SlotActionType.SWAP, mc.player);
    }

    private static Item parseItem(String raw) {
        String id = raw.strip().toLowerCase();
        if (id.isEmpty()) return Items.AIR;
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null || !Registries.ITEM.containsId(ident)) return null;
        return Registries.ITEM.get(ident);
    }
}