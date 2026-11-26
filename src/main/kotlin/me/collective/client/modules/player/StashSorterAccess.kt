package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.utils.sort.ContainerGeometry
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.phys.Vec3

/**
 * Подсистема доступа к сундуку для [StashSorter]: подведение взгляда
 * с ограничением скорости, ожидание остановки Baritone, клик по блоку и
 * работа с открытым экраном контейнера.
 *
 * Выделено из тела [StashSorter] по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика и её константы живут здесь.
 */
object StashSorterAccess {
    /** Допустимое отклонение взгляда, градусы. */
    private const val AIM_EPSILON = 3.0f
    /** Тиков удержания взгляда перед кликом. */
    private const val AIM_HOLD_TICKS = 1

    @JvmStatic
    fun stepOpen(b: StashSorter, canonicalPos: BlockPos): StashSorter.OpenStep {
        val hit = b.nav().openHit(canonicalPos) ?: return StashSorter.OpenStep.NO_ANGLE

        if (!movementSettled(b)) {
            b.alignTicks = 0
            return StashSorter.OpenStep.WAITING
        }

        if (!turnTowards(b, hit.location)) {
            b.alignTicks = 0
            return StashSorter.OpenStep.WAITING
        }

        if (b.alignTicks++ < AIM_HOLD_TICKS) return StashSorter.OpenStep.WAITING

        ClientAccess.collectiveMinecraft.gameMode!!.useItemOn(
            ClientAccess.collectiveMinecraft.player!!, InteractionHand.MAIN_HAND, hit
        )
        ClientAccess.collectiveMinecraft.player!!.swing(InteractionHand.MAIN_HAND)
        return StashSorter.OpenStep.CLICKED
    }

    /** Кликать можно только когда Baritone стоит и клюши отпущены. */
    private fun movementSettled(b: StashSorter): Boolean {
        releaseMovement(b)
        if (b.settleTicks++ < b.settleDelay.value) return false
        return true
    }

    @JvmStatic
    fun releaseMovement(b: StashSorter) {
        b.nav().stop()
        ClientAccess.collectiveMinecraft.options.keyUp.setDown(false)
        ClientAccess.collectiveMinecraft.options.keyDown.setDown(false)
        ClientAccess.collectiveMinecraft.options.keyLeft.setDown(false)
        ClientAccess.collectiveMinecraft.options.keyRight.setDown(false)
        ClientAccess.collectiveMinecraft.options.keyJump.setDown(false)
        ClientAccess.collectiveMinecraft.options.keyShift.setDown(false)
        ClientAccess.collectiveMinecraft.options.keySprint.setDown(false)
    }

    /** Доворачивает взгляд на точку и говорит, попал ли. */
    @JvmStatic
    fun turnTowards(b: StashSorter, point: Vec3): Boolean {
        val player = ClientAccess.collectiveMinecraft.player!!
        val eye = player.getEyePosition()
        val dx = point.x - eye.x
        val dy = point.y - eye.y
        val dz = point.z - eye.z
        val horizontal = Math.sqrt(dx * dx + dz * dz)
        val targetYaw = (Math.toDegrees(Math.atan2(dz, dx)) - 90.0).toFloat()
        val targetPitch = (-Math.toDegrees(Math.atan2(dy, horizontal))).toFloat()

        val maxStep = b.turnSpeed.value
        if (maxStep <= 0) {
            player.yRot = targetYaw
            player.yHeadRot = targetYaw
            player.xRot = Mth.clamp(targetPitch, -90.0f, 90.0f)
            return true
        }

        val dYaw = Mth.wrapDegrees(targetYaw - player.yRot)
        val dPitch = targetPitch - player.xRot

        val maxStepF = maxStep.toFloat()
        val yaw = player.yRot + Mth.clamp(dYaw, -maxStepF, maxStepF)
        player.yRot = yaw
        player.yHeadRot = yaw
        player.xRot = Mth.clamp(
            player.xRot + Mth.clamp(dPitch, -maxStepF, maxStepF), -90.0f, 90.0f
        )

        return Math.abs(dYaw) <= AIM_EPSILON && Math.abs(dPitch) <= AIM_EPSILON
    }

    @JvmStatic
    fun openContainerMenu(b: StashSorter): ChestMenu? {
        if (ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>) return null
        return ClientAccess.collectiveMinecraft.player!!.containerMenu as? ChestMenu
    }

    @JvmStatic
    fun wrongMenuOpen(b: StashSorter): Boolean {
        val player = ClientAccess.collectiveMinecraft.player!!
        return ClientAccess.collectiveMinecraft.gui.screen() is AbstractContainerScreen<*> &&
         player.containerMenu !== player.inventoryMenu &&
         player.containerMenu !is ChestMenu
    }

    @JvmStatic
    fun containerGone(b: StashSorter, pos: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level!!
        return level.hasChunkAt(pos) &&
         !ContainerGeometry.isContainerBlock(level.getBlockState(pos))
    }

    /** Индекс слота инвентаря в номере слотов экрана: у контейнера сверху, у игрока снизу. */
    @JvmStatic
    fun invIndexToMenuId(menu: ChestMenu, invIndex: Int): Int {
        val rows = menu.rowCount
        return rows * 9 + (if (invIndex < 9) invIndex + 27 else invIndex - 9)
    }

    /**
     * Закрыть контейнер. В 1.21.11 [net.minecraft.world.entity.player.Player.closeContainer] *
         у игрока protected, поэтому закрываем экран — он сам отправит пакет закрытия.
     */
    @JvmStatic
    fun closeOpenContainer(b: StashSorter) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val screen = ClientAccess.collectiveMinecraft.gui.screen() ?: return
        val serverContainer = player.containerMenu !== player.inventoryMenu
        val containerScreen = screen is AbstractContainerScreen<*> && screen !is InventoryScreen
        if (serverContainer || containerScreen) screen.onClose()
    }
}
