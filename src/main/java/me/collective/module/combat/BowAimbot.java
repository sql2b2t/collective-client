package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.RotationUtils;
import me.collective.manager.FriendManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Порт BowAimbot из Meteor Client.
 *
 * <p>Пока натянут лук — тихо доводит прицел на цель с упреждением.
 */
public class BowAimbot extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 30.0, 5.0, 60.0, 1.0));
    public final NumberSetting fov = add(new NumberSetting("FOV", 90.0, 5.0, 180.0, 1.0));
    public final BooleanSetting predict = add(new BooleanSetting("Упреждение", true));
    public final BooleanSetting playersOnly = add(new BooleanSetting("Только игроки", true));

    public BowAimbot() {
        super("BowAimbot", "Доводка лука на цель с упреждением.", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isUsingItem() || !mc.player.getActiveItem().isOf(Items.BOW)) return;

        LivingEntity target = getTarget();
        if (target == null) return;

        Vec3d aim = target.getBoundingBox().getCenter();
        if (predict.getValue()) {
            double dist = mc.player.distanceTo(target);
            double t = dist / 60.0;
            Vec3d vel = target.getVelocity();
            aim = aim.add(vel.x * t, vel.y * t, vel.z * t);
        }
        // Поправка на падение стрелы на дистанции
        double dist = mc.player.distanceTo(target);
        aim = aim.add(0, dist * dist * 0.0006, 0);

        Vec3d eye = mc.player.getEyePos();
        Vec3d diff = aim.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))));
        pitch = MathHelper.clamp(pitch, -90f, 90f);

        RotationUtils.setServerRotation(yaw, pitch);
    }

    private LivingEntity getTarget() {
        LivingEntity best = null;
        double bestAngle = fov.getFloatValue() / 2.0;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();

        for (var e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (playersOnly.getValue() && !(living instanceof PlayerEntity)) continue;
            if (living instanceof PlayerEntity p) {
                if (p.isSpectator() || p.isCreative() || FriendManager.getInstance().isFriend(p)) continue;
            }
            if (mc.player.distanceTo(living) > range.getFloatValue()) continue;

            Vec3d toTarget = living.getBoundingBox().getCenter().subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toTarget), -1.0, 1.0)));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = living;
            }
        }
        return best;
    }
}
