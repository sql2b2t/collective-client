package me.collective.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подбор угла планирования для пике на элитре.
 *
 * <p>Перенос {@code bep.hax.util.PorpoiseSolver}, GPL-3.0. Идея: на элитре
 * сервер сверяет перемещение с направлением взгляда, поэтому «нырок» имеет
 * смысл только в узком коридоре. Решение ищется перебором: симулируется полёт
 * на несколько сотен тиков и выбирается тот угол, при котором на единицу
 * времени уходит больше всего по горизонтали.
 *
 * <p>Симуляция дорогая, поэтому результаты кэшируются по огрублённым параметрам
 * (высота, корзина курса, допуски), а уточнение уходит в фоновый поток через
 * {@link MeteorExecutor}. Очевидные решения считаются сразу и синхронно.
 */
public final class PorpoiseSolver {

    public static final int PUMP_PERIOD = 4;

    private static final double GRAVITY = 0.08;
    private static final int SIM_TICKS = 1200;
    private static final int SEARCH_TICKS = 600;

    private static final float MIN_PITCH = 3.0f;
    private static final float MAX_PITCH = 70.0f;
    private static final float MAX_TURN_IN = 85.0f;
    private static final float MIN_DIVE_TICK = -10.0f;

    private static final int BUCKETS = 8;
    private static final float YAW_BUCKET = 11.25f;
    private static final int MAX_CACHE = 64;

    private static final Map<Key, Legs> CACHE = new ConcurrentHashMap<>();
    private static final Set<Key> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PorpoiseSolver() {
    }

    /** Угол для простого случая: одинаковый на пике и на подъёме. */
    public static float solvePitch(double height, float yaw, double yawAlign, double pitchAlign,
                                   double threshold, double anti) {
        return solve(height, yaw, yawAlign, pitchAlign, threshold, anti, false, false, GRAVITY, false)
            .climbBox();
    }

    /**
     * Полный подбор: с поправкой на вертикальную асимметрию и «наддув».
     */
    public static Legs solve(double height, float yaw, double yawAlign, double pitchAlign,
                             double threshold, double anti, boolean asymmetric, boolean pump,
                             double gravity, boolean slowFalling) {
        float key = MathHelper.floorMod(yaw, 90.0f);
        int bucket = Math.min(7, MathHelper.floor(key / YAW_BUCKET));

        boolean refine = asymmetric || pump;

        Key k = new Key(
            (int) Math.round(height), bucket,
            (int) Math.round(yawAlign * 2.0), (int) Math.round(pitchAlign * 2.0),
            (int) Math.round(threshold * 200.0), (int) Math.round(anti * 100.0),
            asymmetric, pump, (int) Math.round(gravity * 2000.0), slowFalling);

        Legs legs = CACHE.get(k);

        if (legs == null) {
            if (CACHE.size() >= MAX_CACHE) CACHE.clear();

            float centre = (bucket + 0.5f) * YAW_BUCKET;
            legs = constant(height, centre, yawAlign, pitchAlign, threshold, anti, gravity,
                slowFalling, !refine);

            CACHE.put(k, legs);
        }

        // уточнение считается в фоне, чтобы не блокировать кадр
        if (refine && !legs.full() && IN_FLIGHT.add(k)) {
            Legs seed = legs;
            float centre = (bucket + 0.5f) * YAW_BUCKET;

            Runnable job = () -> {
                try {
                    CACHE.put(k, refine(seed, height, centre, yawAlign, pitchAlign, threshold, anti,
                        asymmetric, pump, gravity, slowFalling));
                } catch (Throwable t) {
                    // error("Porpoise solve failed: " + t);
                    CACHE.put(k, seed.scored(seed.speed(), seed.diveGround(), seed.climbGround(), true));
                } finally {
                    IN_FLIGHT.remove(k);
                }
            };

            java.util.concurrent.ForkJoinPool.commonPool().execute(job);
        }

        return legs;
    }

    /** Грубый перебор, потом точная доводка вокруг лучшего. */
    private static Legs constant(double height, float yaw, double yawAlign, double pitchAlign,
                                 double threshold, double anti, double gravity, boolean slowFalling,
                                 boolean full) {
        float best = 26.5f;
        double bestSpeed = -1.0;

        for (float p = MIN_PITCH; p <= MAX_PITCH; p += 4.0f) {
            double s = score(Legs.held(p, p), height, yaw, yawAlign, pitchAlign, threshold, anti,
                SIM_TICKS, gravity, slowFalling, null);
            if (s <= bestSpeed) continue;

            bestSpeed = s;
            best = p;
        }

        float lo = Math.max(MIN_PITCH, best - 4.0f);
        float hi = Math.min(MAX_PITCH, best + 4.0f);

        for (float p = lo; p <= hi; p += 0.5f) {
            double s = score(Legs.held(p, p), height, yaw, yawAlign, pitchAlign, threshold, anti,
                SIM_TICKS, gravity, slowFalling, null);
            if (s <= bestSpeed) continue;

            bestSpeed = s;
            best = p;
        }

        return measure(Legs.held(best, best), height, yaw, yawAlign, pitchAlign, threshold, anti,
            gravity, slowFalling, full);
    }

    /** Уточнение: спуск и подъём независимы либо «наддув» фазой пике. */
    private static Legs refine(Legs seed, double height, float yaw, double yawAlign, double pitchAlign,
                               double threshold, double anti, boolean asymmetric, boolean pump,
                               double gravity, boolean slowFalling) {
        Legs best = seed;
        double bestSpeed = seed.speed();

        if (asymmetric) {
            Legs legs = descendHeld(seed.climbBox(), height, yaw, yawAlign, pitchAlign, threshold,
                anti, gravity, slowFalling);

            double s = score(legs, height, yaw, yawAlign, pitchAlign, threshold, anti, SIM_TICKS,
                gravity, slowFalling, null);

            if (s > bestSpeed) {
                best = legs;
                bestSpeed = s;
            }
        }

        if (pump) {
            Legs legs = descendShape(best, height, yaw, yawAlign, pitchAlign, threshold, anti,
                gravity, slowFalling);

            double s = score(legs, height, yaw, yawAlign, pitchAlign, threshold, anti, SIM_TICKS,
                gravity, slowFalling, null);

            if (s > bestSpeed) best = legs;
        }

        return measure(best, height, yaw, yawAlign, pitchAlign, threshold, anti, gravity,
            slowFalling, true);
    }

    /** Подбор пике и подъёма раздельно. */
    private static Legs descendHeld(float seed, double height, float yaw, double yawAlign,
                                    double pitchAlign, double threshold, double anti,
                                    double gravity, boolean slowFalling) {
        float dive = seed;
        float climb = seed;

        double best = score(Legs.held(dive, climb), height, yaw, yawAlign, pitchAlign, threshold,
            anti, SEARCH_TICKS, gravity, slowFalling, null);

        for (int round = 0; round < 2; round++) {
            for (float c = MIN_PITCH; c <= MAX_PITCH; c += 5.0f) {
                double s = score(Legs.held(dive, c), height, yaw, yawAlign, pitchAlign, threshold,
                    anti, SEARCH_TICKS, gravity, slowFalling, null);
                if (s <= best) continue;

                best = s;
                climb = c;
            }

            for (float c = Math.max(MIN_PITCH, climb - 5.0f);
                 c <= Math.min(MAX_PITCH, climb + 5.0f); c += 0.5f) {
                double s = score(Legs.held(dive, c), height, yaw, yawAlign, pitchAlign, threshold,
                    anti, SEARCH_TICKS, gravity, slowFalling, null);
                if (s <= best) continue;

                best = s;
                climb = c;
            }

            for (float d = MIN_PITCH; d <= MAX_PITCH; d += 5.0f) {
                double s = score(Legs.held(d, climb), height, yaw, yawAlign, pitchAlign, threshold,
                    anti, SEARCH_TICKS, gravity, slowFalling, null);
                if (s <= best) continue;

                best = s;
                dive = d;
            }

            for (float d = Math.max(MIN_PITCH, dive - 5.0f);
                 d <= Math.min(MAX_PITCH, dive + 5.0f); d += 0.5f) {
                double s = score(Legs.held(d, climb), height, yaw, yawAlign, pitchAlign, threshold,
                    anti, SEARCH_TICKS, gravity, slowFalling, null);
                if (s <= best) continue;

                best = s;
                dive = d;
            }
        }

        return Legs.held(dive, climb);
    }

    /** Поканальная доводка каждого параметра траектории. */
    private static Legs descendShape(Legs seed, double height, float yaw, double yawAlign,
                                     double pitchAlign, double threshold, double anti,
                                     double gravity, boolean slowFalling) {
        double[] p = seed.params();
        int n = seed.dive().length;

        double best = score(Legs.of(p), height, yaw, yawAlign, pitchAlign, threshold, anti,
            SEARCH_TICKS, gravity, slowFalling, null);

        for (int i = 0; i < p.length; i++) {
            boolean fraction = i == 4 + n;
            boolean turnIn = i == 0 || i == 1 + n;
            boolean diveTick = i >= 1 && i <= n;

            double lo;
            if (fraction) lo = 0.05;
            else if (turnIn) lo = 0.0;
            else if (diveTick) lo = MIN_DIVE_TICK;
            else lo = MIN_PITCH;

            double hi;
            if (fraction) hi = 0.95;
            else if (turnIn || diveTick) hi = MAX_TURN_IN;
            else hi = MAX_PITCH;

            double coarse = fraction ? 0.05 : 5.0;
            double fine = fraction ? 0.01 : 0.5;

            for (double c = lo; c <= hi + 1.0E-9; c += coarse) {
                double old = p[i];
                p[i] = c;

                double s = score(Legs.of(p), height, yaw, yawAlign, pitchAlign, threshold, anti,
                    SEARCH_TICKS, gravity, slowFalling, null);

                if (s > best) {
                    best = s;
                    continue;
                }

                p[i] = old;
            }

            double centre = p[i];

            for (double c = centre - coarse; c <= centre + coarse + 1.0E-9; c += fine) {
                if (c < lo || c > hi) continue;

                double old = p[i];
                p[i] = c;

                double s = score(Legs.of(p), height, yaw, yawAlign, pitchAlign, threshold, anti,
                    SEARCH_TICKS, gravity, slowFalling, null);

                if (s > best) {
                    best = s;
                    continue;
                }

                p[i] = old;
            }
        }

        return Legs.of(p);
    }

    private static Legs measure(Legs legs, double height, float yaw, double yawAlign,
                                double pitchAlign, double threshold, double anti, double gravity,
                                boolean slowFalling, boolean full) {
        double[] ground = new double[2];

        double speed = score(legs, height, yaw, yawAlign, pitchAlign, threshold, anti, SIM_TICKS,
            gravity, slowFalling, ground);

        return legs.scored(speed, ground[0], ground[1], full);
    }

    /**
     * Симуляция полёта и оценка: сколько блоков в секунду уходит по горизонтали.
     *
     * <p>Полёт идёт между двумя «землями» с заданным расстоянием; угол на
     * каждом тике берётся из траектории {@link Legs}, а смещение — из
     * {@link RideWindow}, чтобы уважать тот же коридор, что и в игре.
     */
    private static double score(Legs legs, double height, float yaw, double yawAlign,
                                double pitchAlign, double threshold, double anti, int ticks,
                                double gravity, boolean slowFalling, double[] ground) {
        double half = height / 2.0;

        boolean diving = true;
        int legTick = 0;

        float pitch = legs.pitch(true, 0, 0.0, half);
        Vec3 look = Vec3.fromPolar(pitch, yaw);
        Vec3 v = look.multiply(0.5);

        double alt = 0.0;
        double travelledX = 0.0;
        double travelledZ = 0.0;

        double legGround = 0.0;
        double diveGround = 0.0;
        double climbGround = 0.0;

        int dives = 0;
        int climbs = 0;

        for (int t = 0; t < ticks; t++) {
            if (alt <= -half) {
                if (diving) {
                    // первую треть симуляции не считаем: это разгон
                    if (t > ticks / 3) {
                        diveGround += legGround;
                        dives++;
                    }

                    legGround = 0.0;
                    legTick = 0;
                }

                diving = false;
            } else if (alt >= half) {
                if (!diving) {
                    if (t > ticks / 3) {
                        climbGround += legGround;
                        climbs++;
                    }

                    legGround = 0.0;
                    legTick = 0;
                }

                diving = true;
            }

            float wanted = legs.pitch(diving, legTick, alt, half);
            Vec3 lastLook = look;

            if (wanted != pitch) {
                pitch = wanted;
                look = Vec3.fromPolar(wanted, yaw);
            }

            double g = slowFalling && v.y <= 0.0 ? Math.min(gravity, 0.01) : gravity;

            Vec3 next = RideWindow.ride(v, look, lastLook, pitch, g, threshold, anti,
                yawAlign, pitchAlign, Double.MAX_VALUE);

            // коридор не выдержал — летим как в ванилле
            if (next == null) next = RideWindow.vanillaGlide(v, look, pitch, g);

            v = next;

            alt += v.y;
            travelledX += v.x;
            travelledZ += v.z;
            legGround += v.horizontalLength();
            legTick++;
        }

        if (ground != null) {
            ground[0] = dives > 0 ? diveGround / dives : height * 4.0;
            ground[1] = climbs > 0 ? climbGround / climbs : height * 4.0;
        }

        return Math.sqrt(travelledX * travelledX + travelledZ * travelledZ) / ticks * 20.0;
    }

    /** Траектория пике и подъёма. */
    public record Legs(float topTurnIn, float[] dive, float bottomTurnIn, float climbBox,
                        float climbCoast, float climbSwitch, double speed, double diveGround,
                        double climbGround, boolean full) {

        /** Угол на текущем тике ноги. */
        public float pitch(boolean descending, int legTick, double rel, double half) {
            if (descending) {
                return legTick == 0 ? this.topTurnIn : this.dive[(legTick - 1) % this.dive.length];
            }

            if (legTick == 0) return -this.bottomTurnIn;

            double frac = half <= 0.0 ? 1.0 : (rel + half) / (2.0 * half);
            return frac < this.climbSwitch ? -this.climbBox : -this.climbCoast;
        }

        public double ground(boolean descending) {
            return descending ? this.diveGround : this.climbGround;
        }

        /** Одинаковые углы пике и подъёма — начальное приближение. */
        private static Legs held(float dive, float climb) {
            float[] pattern = new float[4];
            Arrays.fill(pattern, dive);

            return new Legs(dive, pattern, climb, climb, climb, 0.5f, 0.0, 0.0, 0.0, false);
        }

        /** Плоский вектор параметров для поканальной доводки. */
        private double[] params() {
            double[] p = new double[5 + this.dive.length];

            p[0] = this.topTurnIn;
            for (int i = 0; i < this.dive.length; i++) p[1 + i] = this.dive[i];

            p[1 + this.dive.length] = this.bottomTurnIn;
            p[2 + this.dive.length] = this.climbBox;
            p[3 + this.dive.length] = this.climbCoast;
            p[4 + this.dive.length] = this.climbSwitch;

            return p;
        }

        private static Legs of(double[] p) {
            int n = p.length - 5;

            float[] pattern = new float[n];
            for (int i = 0; i < n; i++) pattern[i] = (float) p[1 + i];

            return new Legs((float) p[0], pattern, (float) p[1 + n], (float) p[2 + n],
                (float) p[3 + n], (float) p[4 + n], 0.0, 0.0, 0.0, false);
        }

        private Legs scored(double speed, double diveGround, double climbGround, boolean full) {
            return new Legs(this.topTurnIn, this.dive, this.bottomTurnIn, this.climbBox,
                this.climbCoast, this.climbSwitch, speed, diveGround, climbGround, full);
        }
    }

    /** Огрублённый ключ кэша. */
    private record Key(int height, int yawBucket, int yawAlign, int pitchAlign, int threshold,
                       int anti, boolean asymmetric, boolean pump, int gravity, boolean slowFalling) {
    }
}