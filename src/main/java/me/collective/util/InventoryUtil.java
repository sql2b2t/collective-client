package me.collective.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ContainerInput;

import java.util.function.Predicate;

public class InventoryUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    public static int previousSlot = -1;
    /** Inventory-index helpers deliberately skip foreign containers and user cursors. */
    public static boolean canUsePlayerInventory() {
        return mc.player != null && mc.gameMode != null
            && mc.player.containerMenu == mc.player.inventoryMenu
            && mc.player.containerMenu.getCarried().isEmpty();
    }

    public static boolean testInMainHand(Predicate<ItemStack> predicate) {
        if (mc.player == null) return false;
        return predicate.test(mc.player.getMainHandItem());
    }

    public static boolean testInOffHand(Predicate<ItemStack> predicate) {
        if (mc.player == null) return false;
        return predicate.test(mc.player.getOffhandItem());
    }

    public static FindItemResult findInHotbar(Item... items) {
        return findInHotbar(stack -> {
            for (Item item : items) {
                if (stack.is(item)) return true;
            }
            return false;
        });
    }

    public static FindItemResult findInHotbar(Predicate<ItemStack> predicate) {
        if (mc.player == null) return new FindItemResult(-1, 0);

        int bestSlot = -1;
        int count = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (predicate.test(stack)) {
                if (bestSlot == -1) bestSlot = i;
                count += stack.getCount();
            }
        }

        return new FindItemResult(bestSlot, count);
    }

    /** Оффхенд как слот 40 — только для модулей, умеющих InteractionHand.OFF_HAND. */
    public static FindItemResult findInOffhand(Predicate<ItemStack> predicate) {
        if (mc.player == null) return new FindItemResult(-1, 0);
        ItemStack stack = mc.player.getOffhandItem();
        return predicate.test(stack)
            ? new FindItemResult(40, stack.getCount())
            : new FindItemResult(-1, 0);
    }

    /**
     * Сначала хотбар, если там пусто — оффхенд. Только для модулей, которые
     * реально умеют ставить/использовать предмет из Off InteractionHand.
     */
    public static FindItemResult findInHotbarOrOffhand(Predicate<ItemStack> predicate) {
        FindItemResult hotbar = findInHotbar(predicate);
        return hotbar.found() ? hotbar : findInOffhand(predicate);
    }

    public static FindItemResult findInHotbarOrOffhand(Item... items) {
        return findInHotbarOrOffhand(stack -> {
            for (Item item : items) {
                if (stack.is(item)) return true;
            }
            return false;
        });
    }

    public static FindItemResult find(Item... items) {
        return find(stack -> {
            for (Item item : items) {
                if (stack.is(item)) return true;
            }
            return false;
        });
    }

    public static FindItemResult find(Predicate<ItemStack> predicate) {
        return find(predicate, 0, 35);
    }

    public static FindItemResult find(Predicate<ItemStack> predicate, int start, int end) {
        if (mc.player == null) return new FindItemResult(-1, 0);

        int bestSlot = -1;
        int count = 0;

        for (int i = Math.max(0, start); i <= end && i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (predicate.test(stack)) {
                if (bestSlot == -1) bestSlot = i;
                count += stack.getCount();
            }
        }

        return new FindItemResult(bestSlot, count);
    }

    public static boolean swap(int slot, boolean swapBack) {
        if (mc.player == null) return false;
        // Оффхенд нельзя выбрать как слот хотбара: модуль обязан сам
        // использовать InteractionHand.OFF_HAND.
        if (slot == 40) return false;
        if (slot < 0 || slot > 8) return false;

        if (swapBack && previousSlot == -1) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        } else if (!swapBack) {
            previousSlot = -1;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        syncSelectedSlot();
        return true;
    }

    public static void syncSelectedSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
        }
    }

    public static boolean swap(int slot) {
        return swap(slot, false);
    }

    public static boolean swapBack() {
        if (previousSlot == -1) return false;
        boolean ret = swap(previousSlot, false);
        previousSlot = -1;
        return ret;
    }

    public static void moveToOffhand(int slot) {
        if (!canUsePlayerInventory() || slot < 0 || slot > 35) return;
        int screenSlot = indexToId(slot);
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, screenSlot, 40, ContainerInput.SWAP, mc.player);
    }

    public static void quickMove(int slot) {
        if (!canUsePlayerInventory() || slot < 0 || slot > 35) return;
        int screenSlot = indexToId(slot);
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, screenSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
    }

    public static int indexToId(int index) {
        if (mc.player == null) return -1;
        // In survival inventory:
        // Hotbar (0..8) -> 36..44
        // Armor (36..39) -> 5..8
        // Offhand 40 -> 45
        // Main inventory (9..35) -> 9..35
        if (index >= 0 && index <= 8) return 36 + index;
        if (index >= 36 && index <= 39) return 44 - index;
        if (index == 40) return 45;
        return index >= 9 && index <= 35 ? index : -1;
    }

    public static Action move() {
        return new Action(ContainerInput.PICKUP, true);
    }

    public static Action click() {
        return new Action(ContainerInput.PICKUP, false);
    }

    public static Action quickSwap() {
        return new Action(ContainerInput.SWAP, false);
    }

    public static Action shiftClick() {
        return new Action(ContainerInput.QUICK_MOVE, false);
    }

    public static class Action {
        private ContainerInput type = null;
        private boolean two = false;
        private int from = -1;
        private int to = -1;
        private int data = 0;
        private Action(ContainerInput type, boolean two) {
            this.type = type;
            this.two = two;
        }

        public Action fromId(int id) {
            this.from = id;
            return this;
        }

        public Action from(int index) {
            return fromId(indexToId(index));
        }

        public Action fromHotbar(int i) {
            return from(i);
        }

        public void toId(int id) {
            this.to = id;
            run();
        }

        public void to(int index) {
            toId(indexToId(index));
        }

        public void toHotbar(int i) {
            to(i);
        }

        public void slotId(int id) {
            this.from = id;
            this.to = id;
            run();
        }

        public void slot(int index) {
            slotId(indexToId(index));
        }

        private void run() {
            if (!canUsePlayerInventory() || type == null) return;
            var handler = mc.player.inventoryMenu;
            if (from < 0 || to < 0 || from >= handler.slots.size() || to >= handler.slots.size()) return;
            if (two && from == to) return;
            if (!handler.getSlot(from).mayPickup(mc.player)) return;
            if (two && (!handler.getSlot(to).mayPlace(handler.getSlot(from).getItem())
                || (!handler.getSlot(to).getItem().isEmpty() && !handler.getSlot(to).mayPickup(mc.player)))) return;

            if (type == ContainerInput.SWAP) {
                // SWAP's button is a hotbar inventory index, never a screen slot id.
                data = from >= 36 && from <= 44 ? from - 36 : from == 45 ? 40 : -1;
                if (data == -1) return;
                from = to;
            }
            int source = from;
            clickSlotDirect(source);
            if (two) {
                clickSlotDirect(to);
                // Return displaced items/merge overflow to the source in the same transaction.
                if (!handler.getCarried().isEmpty()) clickSlotDirect(source);
            }
            type = null;
        }

        private void clickSlotDirect(int id) {
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, id, data, type, mc.player);
        }
    }
}