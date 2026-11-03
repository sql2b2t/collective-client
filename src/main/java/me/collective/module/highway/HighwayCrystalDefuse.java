package me.collective.module.highway;

import me.collective.util.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Логика состояния DefuseCrystalTraps билдера: расстрел кристальных ловушек
 * из лука (до 3 выстрелов на кристалл, затем кристалл в игнор-лист).
 *
 * <p>Выделено из тела константы {@code State.DefuseCrystalTraps} по паттерну
 * {@link HighwayRestock}: константа — тонкая оболочка, состояние и логика
 * живут здесь (один экземпляр на JVM — как и прежнее тело константы).
 */
final class HighwayCrystalDefuse {
    static final HighwayCrystalDefuse INSTANCE = new HighwayCrystalDefuse();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Выстрелов на один кристалл до занесения в игнор (защита от зависания). */
    private static final int MAX_SHOTS_PER_CRYSTAL = 3;

    private int cooldown;
    private int shots;
    private EndCrystalEntity target;
    private long drawStartMs;

    private HighwayCrystalDefuse() {
    }

    void start(HighwayBuilderHIG b) {
        if (!InventoryUtil.find(Items.BOW).found()
            || (!InventoryUtil.find(stack -> stack.getItem() instanceof ArrowItem).found() && !mc.player.isCreative())) {
            b.destroyCrystalTraps.setValue(false);
            b.warning("No bow found to destroy crystal traps with. Toggling the setting off.");
            b.setState(HighwayBuilderHIG.State.Forward);
        }

        shots = cooldown = 0;
        target = null;
        drawStartMs = 0L;
    }

    void tick(HighwayBuilderHIG b) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (!InventoryUtil.testInMainHand(stack -> stack.isOf(Items.BOW))) {
            int slot = b.findAndMoveToHotbar(stack -> stack.getItem() instanceof BowItem);
            if (slot == -1) {
                b.destroyCrystalTraps.setValue(false);
                b.warning("No bow found to destroy crystal traps with. Toggling the setting off.");
                b.setState(HighwayBuilderHIG.State.Forward);
                mc.player.stopUsingItem();
                b.drawingBow = false;
                drawStartMs = 0L;
                return;
            }

            InventoryUtil.swap(slot);
        }

        EndCrystalEntity potentialTarget = b.nearestCrystal();
        if (target == null || target.isRemoved()) {
            if (potentialTarget == null) {
                b.setState(HighwayBuilderHIG.State.Forward);
                mc.player.stopUsingItem();
                b.drawingBow = false;
                drawStartMs = 0L;
                return;
            } else {
                target = potentialTarget;
                shots = 0;
            }
        }

        if (shots >= MAX_SHOTS_PER_CRYSTAL) {
            b.ignoreCrystals.add(target);
            b.warning("Detected potential hangup on a crystal. Adding it to ignore list and continuing forward.");
            b.setState(HighwayBuilderHIG.State.Forward);
            mc.player.stopUsingItem();
            b.drawingBow = false;
            drawStartMs = 0L;
            return;
        }

        float[] yp = b.lookAt(target);
        b.rotateTo(yp[0], yp[1]);

        int powerTicks = drawStartMs == 0L ? 0 : (int) ((System.currentTimeMillis() - drawStartMs) / 50L);
        if (BowItem.getPullProgress(powerTicks) >= 1.0f) {
            mc.player.stopUsingItem();
            b.drawingBow = false;
            drawStartMs = 0L;
            cooldown = 20;
            shots++;
        } else {
            if (!b.drawingBow) drawStartMs = System.currentTimeMillis();
            b.drawingBow = true;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
    }
}
