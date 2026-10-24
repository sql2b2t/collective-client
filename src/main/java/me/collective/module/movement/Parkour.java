package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Порт Parkour из ThunderHack.
 *
 * <p>Авто-прыжки через обрывы на бегу. Для хайвеев.
 */
public class Parkour extends Module {
    public final BooleanSetting onlyForward = add(new BooleanSetting("Только вперёд", true));

    public Parkour() {
        super("Parkour", "Автоматически прыгает через ямы.", Category.MOVEMENT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.onGround()) return;
        if (mc.player.isShiftKeyDown()) return;
        if (onlyForward.getValue() && !mc.options.keyUp.isDown()) return;

        Direction dir = mc.player.getDirection();
        BlockPos ahead = mc.player.blockPosition().relative(dir);

        boolean gapAhead = mc.level.getBlockState(ahead).isAir()
            && mc.level.getBlockState(ahead.above(1)).isAir();
        if (!gapAhead) return;

        boolean landing = false;
        for (int i = 1; i <= 3; i++) {
            if (!mc.level.getBlockState(ahead.below(i)).isAir()) {
                landing = true;
                break;
            }
        }
        if (landing) mc.player.jumpFromGround();
    }
}