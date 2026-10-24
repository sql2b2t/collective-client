package me.collective.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Окно просмотра содержимого контейнера по средней кнопке мыши.
 *
 * <p>Перенос {@code TooltipsShulkerBoxScreen}: экран-шилкер на 27 ячеек, куда
 * кладутся стаки из контейнера. Хендлер создаётся с локальным инвентарём и
 * {@code syncId}, которого на сервере нет, поэтому окно только для чтения —
 * сервер клики в нём проигнорирует.
 *
 * <p>У оригинала был подкласс {@code GuiGraphics}, подменявший счётчик на «0»
 * для служебного значения 69. Здесь это не воспроизводится: 69 — внутренняя
 * пасхалка мода, а не признак пустого слота.
 */
public class ContainerPreviewScreen extends ShulkerBoxScreen {

    /** Сервер такого хендлера не знает, клики уйдут в никуда. */
    private static final int FAKE_SYNC_ID = 1337;

    public ContainerPreviewScreen(List<ItemStack> contents, ItemStack container) {
        super(createHandler(contents),
                Minecraft.getInstance().player.getInventory(),
                title(container));
    }

    private static ShulkerBoxScreenHandler createHandler(List<ItemStack> contents) {
        SimpleInventory inventory = new SimpleInventory(27);

        for (int i = 0; i < contents.size() && i < 27; i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) inventory.setStack(i, stack.copy());
        }

        return new ShulkerBoxScreenHandler(FAKE_SYNC_ID,
                Minecraft.getInstance().player.getInventory(), inventory);
    }

    private static Component title(ItemStack container) {
        return container.getName().copy().append(Component.literal(" (просмотр)"));
    }
}