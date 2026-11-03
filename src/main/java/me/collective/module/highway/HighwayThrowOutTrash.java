package me.collective.module.highway;

import me.collective.util.sort.ShulkerDataParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.List;

/**
 * Логика состояния ThrowOutTrash билдера: выброс мусора из инвентаря
 * (TrashItems и «бесполезные» шалкеры), удерживая самый большой стак мусора.
 * После выброса ждёт короткий таймер и возвращается в предыдущее состояние.
 *
 * <p>Выделено из тела константы {@code State.ThrowOutTrash} по паттерну
 * {@link HighwayRestock}: константа — тонкая оболочка, состояние и логика
 * живут здесь (один экземпляр на JVM — как и прежнее тело константы).
 */
final class HighwayThrowOutTrash {
    static final HighwayThrowOutTrash INSTANCE = new HighwayThrowOutTrash();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Тиков ожидания после успешного выброса, чтобы сервер применил дропы. */
    private static final int THREW_PAUSE_TICKS = 10;
    private static final int IDLE_PAUSE_TICKS = 1;

    private int skipSlot;
    private boolean timerEnabled;
    private boolean firstTick;
    private boolean threwItems;
    private int timer;

    private HighwayThrowOutTrash() {
    }

    void start(HighwayBuilderHIG b) {
        int biggestCount = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (itemStack.getItem() instanceof BlockItem && b.trashItems.getValue().contains(itemStack.getItem()) && itemStack.getCount() > biggestCount) {
                biggestCount = itemStack.getCount();
                skipSlot = i;

                if (biggestCount >= 64) break;
            }
        }

        if (biggestCount == 0) skipSlot = -1;
        timerEnabled = false;
        firstTick = true;
        threwItems = false;
    }

    void tick(HighwayBuilderHIG b) {
        if (timerEnabled) {
            if (timer > 0) timer--;
            else b.setState(b.lastState);

            return;
        }

        b.rotateTo(b.dir.yaw, -25);

        if (firstTick) {
            firstTick = false;
            return;
        }

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            b.dropCursor();
            return;
        }

        for (int i = 0; i < 36; i++) {
            if (i == skipSlot) continue;

            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (b.trashItems.getValue().contains(itemStack.getItem())) {
                b.dropSlot(i);
                threwItems = true;
                return;
            }

            if (b.ejectUselessShulkers.getValue() && HighwayInventory.isShulker(itemStack.getItem())) {
                List<ItemStack> contents = ShulkerDataParser.parseShulkerContentsAsList(itemStack);
                boolean eject = true;
                for (ItemStack stack : contents) {
                    if (stack.getItem() instanceof BlockItem bi && (b.blocksToPlace.getValue().contains(bi.getBlock()) || (b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && bi == Items.ENDER_CHEST))) {
                        eject = false;
                        break;
                    }
                    if (stack.isIn(ItemTags.PICKAXES)) {
                        eject = false;
                        break;
                    }
                    if (stack.get(DataComponentTypes.FOOD) != null) {
                        eject = false;
                        break;
                    }
                }

                if (eject) {
                    b.dropSlot(i);
                    threwItems = true;
                    return;
                }
            }
        }

        timerEnabled = true;
        timer = threwItems ? THREW_PAUSE_TICKS : IDLE_PAUSE_TICKS;
    }
}
