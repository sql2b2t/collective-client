package me.collective.util.sort;

import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Геометрия контейнеров: вторая половина двойного сундука, число слотов,
 * признак «это шалкер» и идентификатор сервера для папок с данными.
 */
public final class ContainerGeometry {
    private ContainerGeometry() {
    }

    /**
     * Вторая половина двойного сундука, если она есть и совпадает по типу.
     * Для одинарных сундуков, бочек и разнотипных половин — null.
     */
    @Nullable
    public static BlockPos otherHalf(Level level, BlockPos pos) {
        if (level == null) return null;

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof ChestBlock) && !(block instanceof TrappedChestBlock)) return null;

        if (!state.hasProperty(BlockStateProperties.CHEST_TYPE) || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
        if (state.getValue(BlockStateProperties.CHEST_TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return null;

        Direction connected = ChestBlock.getConnectedDirection(state);
        Block otherBlock = level.getBlockState(pos.relative(connected)).getBlock();
        if (!(otherBlock instanceof ChestBlock)) return null;

        // Половины обычного и зачарованного сундука не образуют двойной сундук
        return (block instanceof TrappedChestBlock) == (otherBlock instanceof TrappedChestBlock)
            ? pos.relative(connected)
            : null;
    }

    /** Меньшая из двух половин — чтобы индекс не дублировал двойные сундуки. */
    public static BlockPos canonical(Level level, BlockPos pos) {
        BlockPos other = otherHalf(level, pos);
        return other == null || pos.compareTo(other) <= 0 ? pos : other;
    }

    public static boolean isContainerBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ChestBlock || block instanceof TrappedChestBlock || block instanceof BarrelBlock;
    }

    public static boolean isShulkerItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.SHULKER_BOXES);
    }

    /** 54 слота у двойного сундука, 27 у одинарного. */
    public static int slotCount(Level level, BlockPos pos) {
        return otherHalf(level, pos) != null ? 54 : 27;
    }

    /**
     * Число слотов контейнера. Если блок загружен — считаем точно,
     * иначе оцениваем по типу и занятым стопкам (чанк мог выгрузиться).
     */
    public static int slotCount(Level level, BlockPos canonical, String containerType, int usedStacks) {
        return level != null && level.isLoaded(canonical) && isContainerBlock(level.getBlockState(canonical))
            ? slotCount(level, canonical)
            : estimatedSlots(containerType, usedStacks);
    }

    public static int estimatedSlots(String containerType, int usedStacks) {
        if (!"chest".equals(containerType) && !"copper_chest".equals(containerType)) return 27;
        return usedStacks > 27 ? 54 : 27;
    }

    /** Идентификатор сервера: папка с данными не должна смешивать серверы. */
    public static String serverId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "unknown";
        if (mc.getCurrentServer() != null) return sanitizeFileName(mc.getCurrentServer().ip);
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null
            ? "singleplayer_" + sanitizeFileName(mc.getSingleplayerServer().getWorldData().getLevelName())
            : "unknown";
    }

    /** Имя папки: спецсимволы и пробелы заменены, регистр сброшен. */
    public static String sanitizeFileName(String name) {
        return name != null && !name.isEmpty()
            ? name.replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", "_").toLowerCase()
            : "unknown";
    }
}