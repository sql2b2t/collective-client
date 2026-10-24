package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.RotationUtils;
import me.collective.manager.FriendManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isUsingItem() || !mc.player.getActiveItem().is(Items.BOW)) return;

        LivingEntity target = getTarget();
        if (target == null) return;

        Vec3 aim = target.getBoundingBox().getCenter();
        if (predict.getValue()) {
            double dist = mc.player.distanceTo(target);
            double t = dist / 60.0;
            Vec3 vel = target.getDeltaMovement();
            aim = aim.add(vel.x * t, vel.y * t, vel.z * t);
        }
        // Поправка на падение стрелы на дистанции
        double dist = mc.player.distanceTo(target);
        aim = aim.add(0, dist * dist * 0.0006, 0);

        Vec3 eye = mc.player.getEyePosition();
        Vec3 diff = aim.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))));
        pitch = Mth.clamp(pitch, -90f, 90f);

        RotationUtils.setServerRotation(yaw, pitch);
    }

    private LivingEntity getTarget() {
        LivingEntity best = null;
        double bestAngle = fov.getFloatValue() / 2.0;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f).normalize();

        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (playersOnly.getValue() && !(living instanceof Player)) continue;
            if (living instanceof Player p) {
                if (p.isSpectator() || p.isCreative() || FriendManager.getInstance().isFriend(p)) continue;
            }
            if (mc.player.distanceTo(living) > range.getFloatValue()) continue;

            Vec3 toTarget = living.getBoundingBox().getCenter().subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(toTarget), -1.0, 1.0)));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = living;
            }
        }
        return best;
    }
}