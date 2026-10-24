package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Порт AntiVoid из ThunderHack.
 *
 * <p>Не даёт упасть в пустоту: гасит падение над войдом. Для городов Края.
 */
public class AntiVoid extends Module {
    public final NumberSetting fallDistance = add(new NumberSetting("Дистанция падения", 3.0, 1.0, 10.0, 0.5));
    public final NumberSetting height = add(new NumberSetting("Высота срабатывания", 0.0, -64.0, 64.0, 1.0));
    public final BooleanSetting jump = add(new BooleanSetting("Авто-прыжок", true));
    /** Кулдаун спасения: после срабатывания модуль молчит N тиков,
     *  чтобы не спамить изменениями velocity. */
    public final NumberSetting cooldown = add(new NumberSetting("Cooldown", 10.0, 0.0, 40.0, 1.0));

    private int cooldownTicks = 0;
    /** Сколько тиков ещё держать автопрыжок после спасения. */
    private int rescueJumpTicks = 0;

    public AntiVoid() {
        super("AntiVoid", "Спасает от падения в пустоту.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        cooldownTicks = 0;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;

        if (rescueJumpTicks > 0) {
            // Прыжок случился (оттолкнулись) — отпускаем клавишу, чтобы не
            // залипнуть в бесконечных прыжках после первого спасения.
            boolean jumped = mc.player.getVelocity().y > 0.3;
            rescueJumpTicks--;
            if (jumped || rescueJumpTicks == 0) {
                mc.options.jumpKey.setPressed(false);
                rescueJumpTicks = 0;
            }
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        if (mc.player.isOnGround()) {
            cooldownTicks = 0;
            return;
        }
        if (mc.player.getY() > height.getFloatValue()) return;
        if (mc.player.fallDistance < fallDistance.getFloatValue()) return;
        if (!isOverVoid()) return;

        // Гасим падение, тянем вверх
        Vec3 vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * 0.7, Math.max(vel.y, -0.15), vel.z * 0.7);
        if (jump.getValue()) {
            mc.options.jumpKey.setPressed(true);
            rescueJumpTicks = 5;
        }
        cooldownTicks = cooldown.getIntValue();
    }

    @Override
    public void onDisable() {
        cooldownTicks = 0;
        rescueJumpTicks = 0;
        if (mc.options != null) mc.options.jumpKey.setPressed(false);
    }

    private final BlockPos.Mutable voidCheckPos = new BlockPos.Mutable();

    private boolean isOverVoid() {
        BlockPos pos = mc.player.getBlockPos();
        for (int y = pos.getY(); y > mc.world.getBottomY() - 10; y--) {
            voidCheckPos.set(pos.getX(), y, pos.getZ());
            if (!mc.world.getBlockState(voidCheckPos).isAir()) return false;
        }
        return true;
    }
}