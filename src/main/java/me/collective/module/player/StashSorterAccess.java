package me.collective.module.player;

import me.collective.util.sort.ContainerGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Подсистема доступа к сундуку для {@link StashSorter}: подведение взгляда
 * с ограничением скорости, ожидание остановки Baritone, клик по блоку и
 * работа с открытым экраном контейнера.
 *
 * <p>Выделено из тела {@link StashSorter} по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика и её константы живут здесь.
 */
final class StashSorterAccess {
    /** Допустимое отклонение взгляда, градусы. */
    private static final float AIM_EPSILON = 3.0F;
    /** Тиков удержания взгляда перед кликом. */
    private static final int AIM_HOLD_TICKS = 1;

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private StashSorterAccess() {
    }

    static StashSorter.OpenStep stepOpen(StashSorter b, BlockPos canonicalPos) {
        BlockHitResult hit = b.nav().openHit(canonicalPos);
        if (hit == null) return StashSorter.OpenStep.NO_ANGLE;

        if (!movementSettled(b)) {
            b.alignTicks = 0;
            return StashSorter.OpenStep.WAITING;
        }

        if (!turnTowards(b, hit.getPos())) {
            b.alignTicks = 0;
            return StashSorter.OpenStep.WAITING;
        }

        if (b.alignTicks++ < AIM_HOLD_TICKS) return StashSorter.OpenStep.WAITING;

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return StashSorter.OpenStep.CLICKED;
    }

    /** Кликать можно только когда Baritone стоит и клюши отпущены. */
    private static boolean movementSettled(StashSorter b) {
        releaseMovement(b);
        if (b.settleTicks++ < b.settleDelay.get()) return false;
        return true;
    }

    static void releaseMovement(StashSorter b) {
        b.nav().stop();
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    /** Доворачивает взгляд на точку и говорит, попал ли. */
    static boolean turnTowards(StashSorter b, Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        float maxStep = b.turnSpeed.get();
        if (maxStep <= 0) {
            mc.player.setYaw(targetYaw);
            mc.player.setHeadYaw(targetYaw);
            mc.player.setPitch(MathHelper.clamp(targetPitch, -90.0F, 90.0F));
            return true;
        }

        float dYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float dPitch = targetPitch - mc.player.getPitch();

        float yaw = mc.player.getYaw() + MathHelper.clamp(dYaw, -maxStep, maxStep);
        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(dPitch, -maxStep, maxStep), -90.0F, 90.0F));

        return Math.abs(dYaw) <= AIM_EPSILON && Math.abs(dPitch) <= AIM_EPSILON;
    }

    @Nullable
    static GenericContainerScreenHandler openContainerMenu(StashSorter b) {
        if (!(mc.currentScreen instanceof HandledScreen)) return null;
        return mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu ? menu : null;
    }

    static boolean wrongMenuOpen(StashSorter b) {
        return mc.currentScreen instanceof HandledScreen
            && mc.player.currentScreenHandler != mc.player.playerScreenHandler
            && !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler);
    }

    static boolean containerGone(StashSorter b, BlockPos pos) {
        return mc.world.isPosLoaded(pos) && !ContainerGeometry.isContainerBlock(mc.world.getBlockState(pos));
    }

    /** Индекс слота инвентаря в номере слотов экрана: у контейнера сверху, у игрока снизу. */
    static int invIndexToMenuId(GenericContainerScreenHandler menu, int invIndex) {
        int rows = menu.getRows();
        return rows * 9 + (invIndex < 9 ? invIndex + 27 : invIndex - 9);
    }

    /**
     * Закрыть контейнер. В 1.21.11 {@code closeHandledScreen()} у игрока
     * protected, поэтому закрываем экран — он сам отправит пакет закрытия.
     */
    static void closeOpenContainer(StashSorter b) {
        if (mc.player == null || mc.currentScreen == null) return;
        boolean serverContainer = mc.player.currentScreenHandler != mc.player.playerScreenHandler;
        boolean containerScreen = mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen);
        if (serverContainer || containerScreen) mc.currentScreen.close();
    }
}
