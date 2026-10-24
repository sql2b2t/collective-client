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
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isOnGround()) return;
        if (mc.player.isSneaking()) return;
        if (onlyForward.getValue() && !mc.options.forwardKey.isPressed()) return;

        Direction dir = mc.player.getHorizontalFacing();
        BlockPos ahead = mc.player.getBlockPos().offset(dir);

        boolean gapAhead = mc.world.getBlockState(ahead).isAir()
            && mc.world.getBlockState(ahead.up(1)).isAir();
        if (!gapAhead) return;

        boolean landing = false;
        for (int i = 1; i <= 3; i++) {
            if (!mc.world.getBlockState(ahead.down(i)).isAir()) {
                landing = true;
                break;
            }
        }
        if (landing) mc.player.jump();
    }
}