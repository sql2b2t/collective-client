package me.collective.client.utils.sort

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.TrappedChestBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.ChestType

/**
 * Геометрия контейнеров: вторая половина двойного сундука, число слотов,
 * признак «это шалкер» и идентификатор сервера для папок с данными.
 */
class ContainerGeometry private constructor() {
    companion object {
        /**
         * Вторая половина двойного сундука, если она есть и совпадает по типу.
         * Для одинарных сундуков, бочек и разнотипных половин — null.
         */
        @JvmStatic
        fun otherHalf(level: Level?, pos: BlockPos): BlockPos? {
            if (level == null) return null

            val state: BlockState = level.getBlockState(pos)
            val block: Block = state.block
            if (block !is ChestBlock && block !is TrappedChestBlock) return null

            if (!state.hasProperty(ChestBlock.TYPE) || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null
            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null

            val connected = ChestBlock.getConnectedDirection(state)
            val otherBlock: Block = level.getBlockState(pos.relative(connected)).block
            if (otherBlock !is ChestBlock) return null

            // Половины обычного и зачарованного сундука не образуют двойной сундук
            return if ((block is TrappedChestBlock) == (otherBlock is TrappedChestBlock)) pos.relative(connected) else null
        }

        /** Меньшая из двух половин — чтобы индекс не дублировал двойные сундуки. */
        @JvmStatic
        fun canonical(level: Level?, pos: BlockPos): BlockPos {
            val other = otherHalf(level, pos)
            return if (other == null || pos <= other) pos else other
        }

        @JvmStatic
        fun isContainerBlock(state: BlockState): Boolean {
            val block = state.block
            return block is ChestBlock || block is TrappedChestBlock || block is BarrelBlock
        }

        @JvmStatic
        fun isShulkerItem(stack: ItemStack?): Boolean {
            return stack != null && !stack.isEmpty && stack.`is`(ItemTags.SHULKER_BOXES)
        }

        /** 54 слота у двойного сундука, 27 у одинарного. */
        @JvmStatic
        fun slotCount(level: Level?, pos: BlockPos): Int {
            return if (otherHalf(level, pos) != null) 54 else 27
        }

        /**
         * Число слотов контейнера. Если блок загружен — считаем точно,
         * иначе оцениваем по типу и занятым стопкам (чанк мог выгрузиться).
         */
        @JvmStatic
        fun slotCount(level: Level?, canonical: BlockPos, containerType: String, usedStacks: Int): Int {
            return if (level != null && level.hasChunkAt(canonical) && isContainerBlock(level.getBlockState(canonical)))
                slotCount(level, canonical)
            else
                estimatedSlots(containerType, usedStacks)
        }

        @JvmStatic
        fun estimatedSlots(containerType: String, usedStacks: Int): Int {
            if (containerType != "chest" && containerType != "copper_chest") return 27
            return if (usedStacks > 27) 54 else 27
        }

        /** Идентификатор сервера: папка с данными не должна смешивать серверы. */
        @JvmStatic
        fun serverId(): String {
            val mc = Minecraft.getInstance()
            if (mc == null) return "unknown"
            val server = mc.currentServer
            if (server != null) return sanitizeFileName(server.ip)
            val integrated = mc.singleplayerServer
            return if (mc.isLocalServer && integrated != null)
                "singleplayer_" + sanitizeFileName(integrated.worldData.levelName)
            else
                "unknown"
        }

        /** Имя папки: спецсимволы и пробелы заменены, регистр сброшен. */
        @JvmStatic
        fun sanitizeFileName(name: String?): String {
            return if (name != null && name.isNotEmpty())
                name.replace("[<>:\"/\\\\|?*]".toRegex(), "_").replace("\\s+".toRegex(), "_").lowercase()
            else
                "unknown"
        }
    }
}
