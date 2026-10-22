package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Порт EntityControl из ThunderHack.
 *
 * <p>Управление ездовыми: разворот за взглядом + ускорение.
 */
public class EntityControl extends Module {
    public final NumberSetting speed = add(new NumberSetting("Ускорение", 0.5, 0.0, 5.0, 0.1));
    public final BooleanSetting yawSync = add(new BooleanSetting("Разворот за взглядом", true));

    public EntityControl() {
        super("EntityControl", "Управление лошадьми, свиньями и лодками.", Category.MOVEMENT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return;

        if (yawSync.getValue()) {
            vehicle.setYaw(mc.player.getYaw());
        }

        double boost = speed.getFloatValue();
        if (boost <= 0) return;

        // Ванильные оси ввода: (strafe = лево - право, forward = вперёд - назад)
        double forward = 0.0;
        double strafe = 0.0;
        if (mc.options.forwardKey.isPressed()) forward += 1.0;
        if (mc.options.backKey.isPressed()) forward -= 1.0;
        if (mc.options.leftKey.isPressed()) strafe += 1.0;
        if (mc.options.rightKey.isPressed()) strafe -= 1.0;
        if (forward == 0.0 && strafe == 0.0) return;

        // Диагонали не дают преимущества: длина входа > 1 нормализуется
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0) {
            forward /= length;
            strafe /= length;
        }

        // Поворот входа на yaw игрока (ванильная movementInputToVelocity)
        Vec3d input = new Vec3d(strafe, 0.0, forward).multiply(boost * 0.15);
        double yawRad = Math.toRadians(mc.player.getYaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        Vec3d v = vehicle.getVelocity();
        vehicle.setVelocity(
            v.x + input.x * cos - input.z * sin,
            v.y,
            v.z + input.z * cos + input.x * sin
        );
    }
}
