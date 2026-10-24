package me.collective.util.tracker;

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Записывает содержимое контейнеров в индекс.
 *
 * <p>Работает по событию обновления инвентаря от сервера: оно приходит и при
 * открытии, и после каждого перекладывания вещей. Дополнительно пишем при
 * закрытии — на случай, если сервер не прислал обновление.
 *
 * <p>Координаты контейнера берём из миксина
 * {@code MultiPlayerGameMode.interactBlock}: обработчик
 * контейнера в 1.21.11 координаты не хранит, а наведения может уже не быть.
 */
public final class ContainerIndexRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger("B2XY/ChestTracker");

    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean shulkerBoxes = true;
    private static boolean enderChests = false;
    private static boolean autosave = true;

    private static BlockPos lastInteractedBlock = null;
    private static BlockPos pendingPos = null;
    private static AbstractContainerMenu lastAbstractContainerMenu = null;
    private static boolean wasInContainerScreen = false;

    private ContainerIndexRecorder() {
    }

    /** Вызывается из миксина в момент попытки взаимодействия с блоком. */
    public static void onBlockInteract(BlockPos pos) {
        if (MC.level == null) return;
        if (isTrackableContainer(MC.level.getBlockState(pos).getBlock())) lastInteractedBlock = pos.immutable();
    }

    public static void setOptions(boolean trackShulkerBoxes, boolean trackEnderChests, boolean autosaveInterval) {
        shulkerBoxes = trackShulkerBoxes;
        enderChests = trackEnderChests;
        autosave = autosaveInterval;
    }

    public static void onTick() {
        if (MC.player == null || MC.level == null) return;

        if (autosave) ChestTrackerDataManager.tickAutosave();
        if (ChestTrackerDataManager.activeCount() <= 0) return;

        boolean currentlyInContainer = isInContainerScreen();
        if (wasInContainerScreen && !currentlyInContainer) recordOnClose();

        if (currentlyInContainer) {
            AbstractContainerMenu handler = MC.player.containerMenu;
            if (handler != null && handler != MC.player.inventoryMenu) {
                lastAbstractContainerMenu = handler;
                if (pendingPos == null && lastInteractedBlock != null) pendingPos = lastInteractedBlock;
            }
        }

        wasInContainerScreen = currentlyInContainer;
    }

    /** Основной путь: сервер прислал содержимое контейнера. */
    public static void onInventoryPacket(ClientboundContainerSetContentPacket packet) {
        if (MC.player == null || MC.level == null) return;
        if (ChestTrackerDataManager.activeCount() <= 0) return;
        // Прилетает с нетти-треда: состояние мира и стейт хендлера можно
        // читать только на главном; переносим и ещё раз проверяем syncId —
        // контейнер мог закрыться, пока задача ждала в очереди.
        MC.execute(() -> handleInventoryPacketOnMainThread(packet));
    }

    private static void handleInventoryPacketOnMainThread(ClientboundContainerSetContentPacket packet) {
        if (MC.player == null || MC.level == null) return;

        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler == null || handler == MC.player.inventoryMenu) return;
        if (packet.containerId() != handler.containerId) return;

        BlockPos pos = pendingPos != null ? pendingPos : lastInteractedBlock;
        if (pos == null) return;
        if (!isTrackableContainer(MC.level.getBlockState(pos).getBlock())) {
            pendingPos = null;
            lastInteractedBlock = null;
            return;
        }

        writeSnapshot(handler, pos);
        pendingPos = pos;
        lastAbstractContainerMenu = handler;
        lastInteractedBlock = null;
    }

    private static void recordOnClose() {
        if (lastAbstractContainerMenu == null || pendingPos == null) {
            reset();
            return;
        }

        BlockPos pos = pendingPos;
        if (MC.level != null && !isTrackableContainer(MC.level.getBlockState(pos).getBlock())) {
            // Блок разобрали, а сундук остался в индексе — это враньё индекса
            ChestTrackerDataManager.getData().removeContainer(pos, currentDimension());
        } else {
            writeSnapshot(lastAbstractContainerMenu, pos);
        }

        reset();
    }

    private static void writeSnapshot(AbstractContainerMenu handler, BlockPos pos) {
        List<ItemStack> items = readContainerSlots(handler);
        ChestTrackerDataManager.getData()
            .trackContainer(pos, currentDimension(), containerType(pos), items);
    }

    /** В инвентаре игрока всегда 36 слотов (27 + 9 хотбара) — отнимаем их. */
    private static List<ItemStack> readContainerSlots(AbstractContainerMenu handler) {
        int containerSlots = Math.max(0, handler.slots.size() - 36);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) items.add(stack.copy());
        }
        return items;
    }

    private static void reset() {
        lastAbstractContainerMenu = null;
        pendingPos = null;
        lastInteractedBlock = null;
        wasInContainerScreen = false;
    }

    private static boolean isInContainerScreen() {
        return MC.gui.screen() != null && MC.player != null
            && MC.player.containerMenu != MC.player.inventoryMenu;
    }

    public static boolean isTrackableContainer(Block block) {
        if (block instanceof ShulkerBoxBlock) return shulkerBoxes;
        if (block instanceof EnderChestBlock) return enderChests;
        return block instanceof ChestBlock
            || block instanceof TrappedChestBlock
            || block instanceof BarrelBlock
            || block instanceof HopperBlock
            || block instanceof DispenserBlock
            || block instanceof DropperBlock;
    }

    /** Тип контейнера строкой — хранится в индексе и влияет на оценку слотов. */
    public static String containerType(BlockPos pos) {
        if (MC.level == null) return "container";
        Block block = MC.level.getBlockState(pos).getBlock();
        if (block instanceof ChestBlock || block instanceof TrappedChestBlock) return "chest";
        if (block instanceof BarrelBlock) return "barrel";
        if (block instanceof ShulkerBoxBlock) return "shulker_box";
        if (block instanceof EnderChestBlock) return "ender_chest";
        if (block instanceof HopperBlock) return "hopper";
        if (block instanceof DispenserBlock) return "dispenser";
        if (block instanceof DropperBlock) return "dropper";
        return "container";
    }

    private static String currentDimension() {
        return MC.level == null ? "unknown" : MC.level.dimension().identifier().toString();
    }
}