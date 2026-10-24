package me.collective.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Окно допустимого смещения при езде на элитре.
 *
 * <p>Перенос {@code bep.hax.util.RideWindow}, GPL-3.0. Сервер считает
 * перемещение планера по своему вектору и допускает узкий коридор: угол
 * между тем, куда смотрит игрок, и тем, куда он едет, ограничен. Здесь
 * считается коридор и точка в нём.
 *
 * <p>Всё в градусах и блоках, как в оригинале; логика перенесена без
 * изменений.
 */
public final class RideWindow {

    /** Порог, за которым сервер перестаёт прощать подъём на фейерверке. */
    public static final double GRIM_FIREWORK_LIMIT = 1.7;

    /** Свободный коридор без ограничений. */
    public static final double FREE = 90.0;

    private RideWindow() {
    }

    /**
     * Точка езды внутри коридора.
     *
     * @return точка смещения или {@code null}, если коридор вырожден
     */
    public static Vec3 ride(Vec3 start, Vec3 look, Vec3 lastLook, float pitch, double gravity,
                              double threshold, double anti, double yawAlignDeg, double pitchAlignDeg,
                              double cap) {
        double[] box = axisBox(look, lastLook, threshold, anti);

        Vec3 predicted = predictGliding(start, look, pitch, gravity);

        // коридор смещается вместе с предсказанной позицией
        double[] w = {
            predicted.x + Math.min(0.0, box[0] - start.x),
            predicted.y + Math.min(0.0, box[1] - start.y),
            predicted.z + Math.min(0.0, box[2] - start.z),
            predicted.x + Math.max(0.0, box[3] - start.x),
            predicted.y + Math.max(0.0, box[4] - start.y),
            predicted.z + Math.max(0.0, box[5] - start.z)
        };

        Vec3 heading = heading(look, predicted, w, yawAlignDeg, pitchAlignDeg, anti);
        double reach = reach(heading, w, anti);

        if (reach <= 1.0E-9) return null;

        Vec3 ride = heading.multiply(Math.min(reach, cap));

        return new Vec3(
            MathHelper.clamp(ride.x, w[0], w[3]),
            MathHelper.clamp(ride.y, w[1], w[4]),
            MathHelper.clamp(ride.z, w[2], w[5]));
    }

    /** Направление езды с поправкой на угловой допуск. */
    private static Vec3 heading(Vec3 look, Vec3 predicted, double[] w, double yawAlignDeg,
                                 double pitchAlignDeg, double anti) {
        if (yawAlignDeg <= 1.0E-6 && pitchAlignDeg <= 1.0E-6) return look;

        boolean vertical = look.horizontalLength() < anti;

        Vec3 corner = new Vec3(
            axisEdge(look.x, predicted.x, w[0], w[3], anti, vertical),
            axisEdge(look.y, 0.0, w[1], w[4], anti, true),
            axisEdge(look.z, predicted.z, w[2], w[5], anti, vertical));

        if (corner.lengthSquared() <= 1.0E-18) return look;

        double lookYaw = Math.toDegrees(Math.atan2(-look.x, look.z));
        double lookPitch = Math.toDegrees(-Math.atan2(look.y, look.horizontalLength()));

        double cornerYaw = corner.horizontalLength() <= 1.0E-9
            ? lookYaw
            : Math.toDegrees(Math.atan2(-corner.x, corner.z));

        double cornerPitch = Math.toDegrees(-Math.atan2(corner.y, corner.horizontalLength()));

        double yaw = lookYaw + MathHelper.clamp(MathHelper.wrapDegrees(cornerYaw - lookYaw),
            -yawAlignDeg, yawAlignDeg);

        double pitch = lookPitch + MathHelper.clamp(cornerPitch - lookPitch,
            -pitchAlignDeg, pitchAlignDeg);

        return Vec3.fromPolar((float) pitch, (float) yaw);
    }

    /** До какой границы коридора доходит луч. */
    private static double reach(Vec3 dir, double[] w, double anti) {
        double reach = Double.MAX_VALUE;

        reach = Math.min(reach, axisReach(dir.x, w[0], w[3], anti));
        reach = Math.min(reach, axisReach(dir.y, w[1], w[4], anti));
        reach = Math.min(reach, axisReach(dir.z, w[2], w[5], anti));

        return reach == Double.MAX_VALUE ? 0.0 : Math.max(0.0, reach);
    }

    private static double axisReach(double dir, double lo, double hi, double anti) {
        if (dir > anti) return hi <= 0.0 ? 0.0 : hi / dir;
        if (dir < -anti) return lo >= 0.0 ? 0.0 : lo / dir;

        return Double.MAX_VALUE;
    }

    private static double axisEdge(double dir, double keep, double lo, double hi, double anti,
                                   boolean holdZero) {
        if (dir > anti) return hi;
        if (dir < -anti) return lo;

        return MathHelper.clamp(holdZero ? 0.0 : keep, lo, hi);
    }

    /** Коридор допустимых направлений: грубый, по двум соседним взглядам. */
    private static double[] axisBox(Vec3 look, Vec3 lastLook, double threshold, double a) {
        double minX = Math.min(-a, look.x) + Math.min(-a, lastLook.x);
        double minY = Math.min(-a, look.y) + Math.min(-a, lastLook.y);
        double minZ = Math.min(-a, look.z) + Math.min(-a, lastLook.z);

        double maxX = Math.max(a, look.x) + Math.max(a, lastLook.x);
        double maxY = Math.max(a, look.y) + Math.max(a, lastLook.y);
        double maxZ = Math.max(a, look.z) + Math.max(a, lastLook.z);

        return new double[]{
            Math.max(-threshold, minX * threshold),
            Math.max(-threshold, minY * threshold),
            Math.max(-threshold, minZ * threshold),
            Math.min(threshold, maxX * threshold),
            Math.min(threshold, maxY * threshold),
            Math.min(threshold, maxZ * threshold)
        };
    }

    /** Предсказание планера при планировании на элитре. */
    public static Vec3 predictGliding(Vec3 old, Vec3 look, float pitchDeg, double gravity) {
        float pitchRad = pitchDeg * ((float) Math.PI / 180);

        double horizSqrt = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizLen = old.horizontalLength();

        double vertCos = Math.cos(pitchRad);
        vertCos = vertCos * vertCos * Math.min(1.0, look.length() / 0.4);

        Vec3 v = old.add(0.0, gravity * (-1.0 + vertCos * 0.75), 0.0);

        // пикирование под углом добавляет горизонтальное движение
        if (v.y < 0.0 && horizSqrt > 0.0) {
            double d = v.y * -0.1 * vertCos;
            v = v.add(look.x * d / horizSqrt, d, look.z * d / horizSqrt);
        }

        if (pitchRad < 0.0f && horizSqrt > 0.0) {
            double d = horizLen * -MathHelper.sin(pitchRad) * 0.04;
            v = v.add(-look.x * d / horizSqrt, d * 3.2, -look.z * d / horizSqrt);
        }

        if (horizSqrt > 0.0) {
            v = v.add(
                (look.x / horizSqrt * horizLen - v.x) * 0.1,
                0.0,
                (look.z / horizSqrt * horizLen - v.z) * 0.1);
        }

        return v.multiply(0.99f, 0.98f, 0.99f);
    }

    /** Планирование с толчком от сервера. */
    public static Vec3 vanillaGlide(Vec3 v, Vec3 look, float pitch, double gravity) {
        Vec3 pushed = v.add(
            look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5,
            look.y * 0.1 + (look.y * 1.5 - v.y) * 0.5,
            look.z * 0.1 + (look.z * 1.5 - v.z) * 0.5);

        return predictGliding(pushed, look, pitch, gravity);
    }
}