package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {
    public static Speed INSTANCE;

    public enum Mode {
        GRIM_BHOP, NCP, STRICT_STRAFE, VANILLA, MATRIX_DAMAGE
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Режим", Mode.GRIM_BHOP));
    public final NumberSetting speedMultiplier = add(new NumberSetting("Множитель", 1.25, 1.0, 3.0, 0.05));
    public final BooleanSetting autoJump = add(new BooleanSetting("Авто прыжок", true));
    public final BooleanSetting pauseInWater = add(new BooleanSetting("Пауза в воде", true));
    public final BooleanSetting pauseSneak = add(new BooleanSetting("Пауза на Shift", true));

    public Speed() {
        super("Speed", "Увеличивает скорость перемещения игрока с поддержкой различных античитов", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onUpdate() {
        if (!isToggled() || mc.player == null || mc.level == null) return;

        if (pauseInWater.getValue() && mc.player.isInWater()) return;
        if (pauseSneak.getValue() && mc.player.isShiftKeyDown()) return;

        boolean moving = mc.player.input != null && (mc.player.input.getMoveVector().y != 0.0f || mc.player.input.getMoveVector().x != 0.0f);
        if (!moving) return;

        if (autoJump.getValue() && mc.player.onGround()) {
            mc.player.jumpFromGround();
        }

        switch (mode.getValue()) {
            case GRIM_BHOP -> {
                mc.player.setSprinting(true);
                if (mc.player.onGround()) {
                    mc.player.jumpFromGround();
                }
            }
            case VANILLA -> {
                double speed = 0.2873 * speedMultiplier.getValue();
                strafe(speed);
            }
            case NCP -> {
                if (mc.player.onGround()) {
                    double speed = getBaseSpeed() * 1.35;
                    strafe(speed);
                } else {
                    double speed = Math.max(getBaseSpeed(), getSpeed() * 0.985);
                    strafe(speed);
                }
            }
            case STRICT_STRAFE -> {
                double speed = getBaseSpeed();
                strafe(speed);
            }
            case MATRIX_DAMAGE -> {
                if (mc.player.hurtTime > 0) {
                    double boost = getBaseSpeed() * speedMultiplier.getValue();
                    strafe(boost);
                } else if (mc.player.onGround()) {
                    strafe(getBaseSpeed() * 1.15);
                }
            }
        }
    }

    public static double getBaseSpeed() {
        double base = 0.2873;
        if (mc.player != null && mc.player.hasEffect(MobEffects.SPEED)) {
            int amp = mc.player.getEffect(MobEffects.SPEED).getAmplifier();
            base *= 1.0 + 0.2 * (amp + 1);
        }
        return base;
    }

    public static double getSpeed() {
        if (mc.player == null) return 0.0;
        Vec3 vel = mc.player.getDeltaMovement();
        return Math.sqrt(vel.x * vel.x + vel.z * vel.z);
    }

    public static void strafe(double speed) {
        if (mc.player == null || mc.player.input == null) return;

        net.minecraft.world.phys.Vec2 mov = mc.player.input.getMoveVector();
        float forward = mov.y;
        float sideways = mov.x;
        float yaw = mc.player.getYRot();

        if (forward == 0.0f && sideways == 0.0f) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0.0, v.y, 0.0);
            return;
        }

        if (forward != 0.0f) {
            if (sideways > 0.0f) {
                yaw += (forward > 0.0f ? -45 : 45);
            } else if (sideways < 0.0f) {
                yaw += (forward > 0.0f ? 45 : -45);
            }
            sideways = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }

        double rad = Math.toRadians(yaw + 90.0f);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double x = forward * speed * cos + sideways * speed * sin;
        double z = forward * speed * sin - sideways * speed * cos;

        Vec3 v = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(x, v.y, z);
    }
}