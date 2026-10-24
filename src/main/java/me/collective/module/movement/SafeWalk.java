package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PlayerMoveEvent;
import me.collective.module.Module;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Clips horizontal walking at unsupported edges without forging sneak packets. */
public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", "Предотвращает падение с краёв блоков (как при зажатом Shift).", Category.MOVEMENT);
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isOnGround()
            || mc.player.getAbilities().flying || mc.player.isGliding()
            || event.type != MovementType.SELF) return;
        Vec3 movement = event.movement;
        double x = movement.x;
        double z = movement.z;
        if (!Double.isFinite(x) || !Double.isFinite(z)) return;
        // Bound work even if another module supplies an unusually large displacement.
        if (Math.abs(x) > 10 || Math.abs(z) > 10) {
            event.movement = new Vec3(0, movement.y, 0);
            return;
        }
        while (x != 0 && unsupported(x, 0)) x = towardZero(x);
        while (z != 0 && unsupported(0, z)) z = towardZero(z);
        while (x != 0 && z != 0 && unsupported(x, z)) {
            x = towardZero(x);
            z = towardZero(z);
        }
        event.movement = new Vec3(x, movement.y, z);
    }

    private boolean unsupported(double x, double z) {
        return mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(x, -0.5, z));
    }

    private static double towardZero(double value) {
        return Math.abs(value) <= 0.05 ? 0 : value - Math.copySign(0.05, value);
    }
}