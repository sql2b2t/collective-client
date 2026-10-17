package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.KeybindSetting;
import me.collective.setting.ModeSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

/**
 * Порт ElytraSwap из ThunderHack-Recode.
 *
 * <p>Режим Enable: при включении один раз меняет нагрудник на элитру (или
 * обратно) и самоотключается. Режим Bind: смена по кнопке, запуск фейерверка
 * по кнопке и авто-фейерверк при старте планирования.
 *
 * <p>Grim-обход: замена в хотбаре — одним SWAP-кликом (1 пакет, атомарно);
 * замена из инвентаря — очередью по 1 PICKUP-клику в тик на render-треде
 * (без пачки кликов в один тик и без потоков). Открытый экран прерывает
 * очередь, чтобы курсор не остался с вещью.
 */
public class ElytraSwap extends Module {
    public static ElytraSwap INSTANCE;

    public final BooleanSetting delay = add(new BooleanSetting("Delay", true));
    public final ModeSetting mode = add(new ModeSetting("Mode", "Enable", "Enable", "Bind"));
    public final KeybindSetting switchButton = add(new KeybindSetting("SwitchButton", -1));
    public final KeybindSetting fireWorkButton = add(new KeybindSetting("FireWorkButton", -1));
    public final BooleanSetting startFireWork = add(new BooleanSetting("StartFireWork", true));
    public final ModeSetting fireWorkMode = add(new ModeSetting("FireWorkMode", "Normal", "Silent", "Normal"));

    public static boolean swapping = false;
    private long lastSwitch = 0L;
    private long lastFirework = 0L;

    /** Очередь PICKUP-кликов (слоты хендлера), по одному в тик. */
    private int[] clickQueue = null;
    private int queueIndex = 0;
    private boolean queueDisable = false;

    public ElytraSwap() {
        super("ElytraSwap", "Быстрая смена нагрудника на элитру и обратно + авто-фейерверк.", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mode.process("Enable")) swapChest(true);
    }

    @Override
    public void onDisable() {
        clickQueue = null;
        queueIndex = 0;
        swapping = false;
    }

    @Override
    public void onUpdate() {
        if (clickQueue != null) {
            processQueue();
            return;
        }
        if (!mode.process("Bind") || mc.player == null || mc.currentScreen != null) return;
        long now = System.currentTimeMillis();
        if (switchButton.getValue() != -1 && switchButton.isPressed() && now - lastSwitch >= 500) {
            lastSwitch = now;
            swapChest(false);
        }
        if (fireWorkButton.getValue() != -1 && fireWorkButton.isPressed()
            && now - lastFirework >= 500 && mc.player.isGliding()) {
            lastFirework = now;
            useFireWork();
        }
    }

    /** Один клик в тик из очереди (render-тред). */
    private void processQueue() {
        if (mc.player == null || mc.interactionManager == null) {
            clickQueue = null;
            swapping = false;
            return;
        }
        if (mc.currentScreen != null) {
            clickQueue = null;
            swapping = false;
            info("Свап прерван: открыт инвентарь!");
            if (queueDisable) setToggled(false);
            return;
        }
        clickSlot(clickQueue[queueIndex++]);
        if (queueIndex >= clickQueue.length) {
            clickQueue = null;
            sendClose(mc.player.currentScreenHandler.syncId);
            swapping = false;
            if (queueDisable) disable("Свапнул! Отключаю..");
            else info("Свапнул!");
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ClientCommandC2SPacket cmd)) return;
        if (cmd.getMode() != ClientCommandC2SPacket.Mode.START_FALL_FLYING) return;
        if (!mode.process("Bind") || !startFireWork.getValue()) return;
        useFireWork();
    }

    public void useFireWork() {
        if (mc.player == null || mc.interactionManager == null) return;
        boolean silent = fireWorkMode.process("Silent");

        FindItemResult hotbar = InventoryUtil.findInHotbar(Items.FIREWORK_ROCKET);
        if (hotbar.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }
        if (hotbar.isHotbar()) {
            if (silent) {
                InventoryUtil.swap(hotbar.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InventoryUtil.swapBack();
            } else {
                mc.player.getInventory().selectedSlot = hotbar.slot();
                mc.interactionManager.syncSelectedSlot();
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            sendSwing();
            return;
        }

        FindItemResult inv = InventoryUtil.find(Items.FIREWORK_ROCKET);
        if (inv.found() && inv.isMain()) {
            int syncId = mc.player.currentScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, inv.slot(), mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            sendClose(syncId);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            sendSwing();
            if (silent) {
                mc.interactionManager.clickSlot(syncId, inv.slot(), mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                sendClose(syncId);
            }
            return;
        }

        info("У тебя нет фейерверков!");
    }

    public static int getChestPlateSlot() {
        Item[] items = {Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE,
            Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.LEATHER_CHESTPLATE};
        for (Item item : items) {
            FindItemResult slot = InventoryUtil.find(item);
            if (slot.found()) return slot.slot();
        }
        return -1;
    }

    private void swapChest(boolean disable) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) {
            if (disable) disable("Закрой инвентарь и повтори!");
            else info("Закрой инвентарь и повтори!");
            return;
        }
        if (clickQueue != null) return;
        FindItemResult elytra = InventoryUtil.find(Items.ELYTRA);

        if (mc.player.getInventory().getStack(38).isOf(Items.ELYTRA)) {
            int invSlot = getChestPlateSlot();
            if (invSlot == -1) {
                if (disable) disable("У тебя нет нагрудника!");
                else info("У тебя нет нагрудника!");
                return;
            }
            if (invSlot >= 0 && invSlot < 9) {
                fastSwap(invSlot);
            } else {
                startQueue(new int[]{invSlot, 6, invSlot}, disable);
                return;
            }
        } else if (elytra.found()) {
            // На souver: glide-start отправит сама ванилла, когда полетим.
            // Свой START_FALL_FLYING сюда слать нельзя — будет дубль и BadPacketsJ.
            int invSlot = elytra.slot();
            if (invSlot >= 0 && invSlot < 9) {
                fastSwap(invSlot);
            } else {
                startQueue(new int[]{invSlot, 6, invSlot}, disable);
                return;
            }
        } else {
            if (disable) disable("У тебя нет элитры!");
            else info("У тебя нет элитры!");
            return;
        }

        if (disable) disable("Свапнул! Отключаю..");
        else info("Свапнул!");
    }

    /** Один SWAP-клик: слот брони 6 <-> хотбар. Без курсора и задержек. */
    private void fastSwap(int hotbarSlot) {
        if (mc.player == null || mc.interactionManager == null) return;
        swapping = true;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, hotbarSlot, SlotActionType.SWAP, mc.player);
        sendClose(mc.player.currentScreenHandler.syncId);
        swapping = false;
    }

    /** Очередь PICKUP-кликов: сразу все (Delay выкл) или по одному в тик. */
    private void startQueue(int[] slots, boolean disable) {
        if (!delay.getValue()) {
            swapping = true;
            for (int slot : slots) clickSlot(slot);
            sendClose(mc.player.currentScreenHandler.syncId);
            swapping = false;
            if (disable) disable("Свапнул! Отключаю..");
            else info("Свапнул!");
            return;
        }
        clickQueue = slots;
        queueIndex = 0;
        queueDisable = disable;
        swapping = true;
    }

    private void clickSlot(int slot) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void sendClose(int syncId) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
        }
    }

    private void sendSwing() {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    private void disable(String msg) {
        info(msg);
        setToggled(false);
    }
}
