package me.collective.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Подбор траектории «отскоков» на элитре под потолок.
 *
 * <p>Перенос {@code bep.hax.util.BounceSolver}, GPL-3.0. Задача: игрок
 * подбрасывает себя фейерверками у низкого потолка, и скорость зависит от того,
 * как меняется угол по тикам после прыжка. Решение ищется моделированием:
 * та же физика планирования и ходьбы, что и в игре, но без реального мира.
 *
 * <p>Высота потолка задаётся отдельно, потому что удар о него обнуляет
 * вертикальную скорость — это и ломает наивную схему.
 */
public final class BounceSolver {

    private static final double GRAVITY = 0.08;

    public static final double GLIDE_HEIGHT = 0.6;
    public static final double MAX_USEFUL_HEADROOM = 2.0;
    public static final int SCHEDULE_TICKS = 16;
    public static final int MAX_PERSIST = 40;

    private static final int SIM_TICKS = 1000;
    private static final int AVG_TICKS = 400;

    private static final int MIN_PITCH = 0;
    private static final int MAX_PITCH = 90;

    /** Сколько самых удачных затравок доводится точечно. */
    private static final int THOROUGH_SEEDS = 6;

    private BounceSolver() {
    }

    /** Свободная высота над игроком в его колонке. */
    public static double measureHeadroom(Player player, Level world) {
        AABB box = new AABB(player.getX() - 0.3, player.getY(), player.getZ() - 0.3,
            player.getX() + 0.3, player.getY() + 0.6, player.getZ() + 0.3);

        double free = 0.0;

        for (double rise = 0.05; rise <= MAX_USEFUL_HEADROOM && world.noCollision(box.move(0.0, rise, 0.0)); rise += 0.05) {
            free = rise;
        }

        return free;
    }

    /**
     * Моделирование полёта по расписанию углов.
     *
     * @param fn          угол по номеру тика после прыжка и вертикальной скорости
     * @param headroom    свободная высота
     * @param persist     сколько тиков планирование держится после касания
     * @param recordTable куда записать углы, если нужно
     * @param cycleOut    средняя длина цикла, если нужно
     * @return горизонтальная скорость в блоках в секунду
     */
    public static double simulate(PitchFn fn, double headroom, int persist, float[] recordTable,
                                  int[] cycleOut) {
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;

        double y = 0.3;
        double z = 0.0;
        double zMark = 0.0;

        boolean onGround = false;
        boolean gliding = true;
        boolean prevKey = false;

        // очередь моментов, когда планирование закончится
        int[] clears = new int[64];
        int clearHead = 0;
        int clearTail = 0;

        int tsj = 0;
        int lastLanding = -1;
        int cycleSum = 0;
        int cycleN = 0;

        boolean open = headroom >= MAX_USEFUL_HEADROOM;
        double ceilingH = headroom + GLIDE_HEIGHT;

        for (int t = 0; t < SIM_TICKS; t++) {
            boolean key;

            while (clearHead != clearTail && clears[clearHead & 0x3F] <= t) {
                clearHead++;
                gliding = false;
            }

            key = onGround || !gliding && !prevKey;
            if (key && !prevKey && !onGround && !gliding) gliding = true;

            prevKey = key;

            if (key && onGround) {
                vy = Math.max(0.42, vy);
                vz += 0.2;
                tsj = 0;
            }

            float pitch = fn.pitch(tsj, vy);

            if (recordTable != null && t >= 600 && tsj < recordTable.length) {
                recordTable[tsj] = pitch;
            }

            // хитбокс не проходит в потолок — это и есть ограничение
            double hitbox = gliding ? GLIDE_HEIGHT
                : (open || ceilingH >= 1.8 ? 1.8 : (ceilingH >= 1.5 ? 1.5 : GLIDE_HEIGHT));

            double free = open ? Double.MAX_VALUE : ceilingH - hitbox;

            boolean wasGround = onGround;

            if (gliding) {
                double f = pitch * (Math.PI / 180);

                double lookZ = Mth.cos(f);
                double d = Math.abs(lookZ);
                double e = Math.sqrt(vx * vx + vz * vz);
                double h = Mth.square(Mth.cos(f));

                vy += GRAVITY * (-1.0 + h * 0.75);

                // пикирование под углом даёт горизонтальное ускорение
                if (vy < 0.0 && d > 0.0) {
                    double i = vy * -0.1 * h;
                    vz += lookZ * i / d;
                    vy += i;
                }

                if (d > 0.0) vz += (lookZ / d * e - vz) * 0.1;

                vx *= 0.99;
                vz *= 0.99;

                double ny = y + (vy *= 0.98);

                onGround = false;
                if (ny <= 0.0) {
                    ny = 0.0;
                    vy = 0.0;
                    onGround = true;
                } else if (ny >= free) {
                    // упёрлись в потолок
                    ny = free;
                    vy = 0.0;
                }

                y = ny;
                z += vz;
            } else {
                double moveZ = (vz += (onGround ? 0.13 : 0.026) * 0.98);
                double fric = onGround ? 0.546 : 0.91;

                double ny = y + vy;

                onGround = false;
                if (ny <= 0.0) {
                    ny = 0.0;
                    vy = 0.0;
                    onGround = true;
                } else if (ny >= free) {
                    ny = free;
                    vy = 0.0;
                }

                y = ny;
                z += moveZ;

                vx *= fric;
                vz *= fric;
                vy = (vy - GRAVITY) * 0.98;
            }

            tsj++;

            if (onGround) {
                if (persist > 0 && clearTail - clearHead < 64) {
                    clears[clearTail++ & 0x3F] = t + persist;
                }

                if (!wasGround) {
                    if (lastLanding >= 0 && t >= 600) {
                        cycleSum += t - lastLanding;
                        cycleN++;
                    }

                    lastLanding = t;
                }
            }

            if (t != 600) continue;

            // скорость меряем со второго отрезка: первый — разгон
            zMark = z;
        }

        if (cycleOut != null) {
            cycleOut[0] = cycleN > 0 ? (int) Math.round((double) cycleSum / cycleN) : 0;
        }

        return (z - zMark) / AVG_TICKS * 20.0;
    }

    /**
     * Лучший равномерный угол для данной высоты потолка.
     *
     * <p>Совместимость с ElytraBounce: раньше там был свой упрощённый
     * {@code solvePitch}, теперь используется полный перенесённый поиск.
     */
    public static float solvePitch(double headroom) {
        return solve(headroom, false).pitchAt(0);
    }

    /** Скорость при фиксированном угле — адаптер под ElytraBounce. */
    public static double simulateSpeed(float pitch, double headroom) {
        float[] table = new float[16];
        Arrays.fill(table, pitch);

        return simulate((tsj, vy) -> table[Math.min(tsj, table.length - 1)], headroom, 0, null, null);
    }

    public static double expectedSpeed(float[] table, double headroom, int[] persistWeights) {
        return score(table, headroom, smoothWeights(persistWeights));
    }

    public static double expectedSpeed(float[] table, double headroom) {
        return simulate((tsj, vy) -> table[Math.min(tsj, table.length - 1)], headroom, 0, null, null);
    }

    /** Средняя скорость по набору длительностей планирования. */
    private static double score(float[] table, double headroom, double[] weights) {
        PitchFn fn = (tsj, vy) -> table[Math.min(tsj, table.length - 1)];

        double sum = 0.0;

        for (int p = 2; p <= MAX_PERSIST; p++) {
            if (weights[p] <= 0.0) continue;

            sum += weights[p] * simulate(fn, headroom, p, null, null);
        }

        return sum;
    }

    /** Размазывает наблюдаемые длительности: соседние значения получают половину веса. */
    private static double[] smoothWeights(int[] persistWeights) {
        double[] w = new double[MAX_PERSIST + 1];
        double total = 0.0;

        if (persistWeights != null) {
            for (int p = 2; p <= MAX_PERSIST && p < persistWeights.length; p++) {
                if (persistWeights[p] <= 0) continue;

                w[p] += persistWeights[p];

                if (p > 2) w[p - 1] += persistWeights[p] / 2.0;
                if (p >= MAX_PERSIST) continue;

                w[p + 1] += persistWeights[p] / 2.0;
            }

            for (double v : w) total += v;
        }

        // ничего не набралось — берём правдоподобное распределение
        if (total <= 0.0) {
            w[3] = 1.0;
            w[4] = 2.0;
            w[5] = 1.0;
            total = 4.0;
        }

        for (int p = 0; p < w.length; p++) w[p] /= total;

        return w;
    }

    private static int dominant(double[] weights) {
        int best = 4;

        for (int p = 2; p <= MAX_PERSIST; p++) {
            if (weights[p] > weights[best]) best = p;
        }

        return best;
    }

    public static Plan solve(double headroom, int[] persistWeights, boolean schedule) {
        double[] w = smoothWeights(persistWeights);

        return search(headroom, tbl -> score(tbl, headroom, w), dominant(w), schedule, false);
    }

    public static Plan solve(double headroom, boolean schedule) {
        return search(headroom, tbl -> expectedSpeed(tbl, headroom), 0, schedule, true);
    }

    public static double optimize(double headroom, int persist, float[] schedule, int[] cycleOut) {
        Plan plan = solve(headroom, true);
        if (schedule != null) {
            System.arraycopy(plan.pitchByTick, 0, schedule, 0, Math.min(schedule.length, plan.pitchByTick.length));
        }
        if (cycleOut != null && cycleOut.length > 0) {
            cycleOut[0] = plan.cycle;
        }
        return plan.speed;
    }

    /**
     * Поиск расписания углов.
     *
     * <p>Сначала грубые равномерные углы, потом двухступенчатые «вверх-вниз», и
     * только для обещанного по расписанию варианта — точечная доводка по
     * каждому тику.
     */
    private static Plan search(double headroom, ToDoubleFunction<float[]> scorer, int seedPersist,
                               boolean schedule, boolean thorough) {
        float[] best = new float[16];
        double bestScore = -1.0;

        List<float[]> seeds = new ArrayList<>();
        List<Double> seedScores = new ArrayList<>();

        for (int p = MIN_PITCH + 20; p <= MAX_PITCH; p++) {
            float[] tbl = new float[16];
            Arrays.fill(tbl, p);

            double s = scorer.applyAsDouble(tbl);

            if (s > bestScore) {
                bestScore = s;
                best = tbl;
            }

            if (!thorough) continue;

            seeds.add(tbl);
            seedScores.add(s);
        }

        if (schedule) {
            for (int up = 30; up <= 90; up += 5) {
                for (int down = 0; down <= 90; down += 5) {
                    float u = up;
                    float d = down;

                    float[] tbl = new float[16];
                    Arrays.fill(tbl, u);

                    // пике заканчивается, дальше идёт подъём
                    simulate((tsj, vy) -> vy >= 0.0 ? u : d, headroom, seedPersist, tbl, null);

                    double s = scorer.applyAsDouble(tbl);

                    if (s > bestScore) {
                        bestScore = s;
                        best = tbl;
                    }

                    if (!thorough) continue;

                    seeds.add(tbl);
                    seedScores.add(s);
                }
            }

            if (thorough) {
                // три фазы: разгон, пике, подъём
                for (int a = 3; a <= 8; a++) {
                    for (int b = a + 1; b <= 13; b++) {
                        for (int up = 60; up <= 90; up += 10) {
                            for (int d1 = 0; d1 <= 90; d1 += 15) {
                                for (int d2 = 0; d2 <= 90; d2 += 15) {
                                    float[] tbl = new float[16];

                                    for (int k = 0; k < 16; k++) {
                                        tbl[k] = k < a ? up : (k < b ? d1 : d2);
                                    }

                                    double s = scorer.applyAsDouble(tbl);
                                    if (s > bestScore) {
                                        bestScore = s;
                                        best = tbl;
                                    }

                                    seeds.add(tbl);
                                    seedScores.add(s);
                                }
                            }
                        }
                    }
                }
            }

            if (!thorough) {
                bestScore = descend(best, bestScore, scorer, 6, 3);
            } else {
                Integer[] order = new Integer[seeds.size()];
                for (int i = 0; i < order.length; i++) order[i] = i;

                Arrays.sort(order, Comparator.comparingDouble(i -> -seedScores.get((int) i)));

                for (int n = 0; n < Math.min(THOROUGH_SEEDS, order.length); n++) {
                    float[] tbl = seeds.get(order[n]).clone();

                    double s = descend(tbl, seedScores.get(order[n]), scorer, 6, 3);
                    if (s <= bestScore) continue;

                    bestScore = s;
                    best = tbl;
                }

                bestScore = descend(best, bestScore, scorer, 2, 2);
                bestScore = descend(best, bestScore, scorer, 1, 1);
            }
        }

        float[] table = best;
        int[] cyc = new int[1];

        simulate((tsj, vy) -> table[Math.min(tsj, table.length - 1)], headroom, seedPersist, null, cyc);

        return new Plan(table, bestScore, cyc[0], headroom);
    }

    /** Точечная доводка каждого тика расписания. */
    private static double descend(float[] table, double score, ToDoubleFunction<float[]> scorer,
                                  int step, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            boolean improved = false;

            for (int k = 0; k < table.length; k++) {
                float keep = table[k];

                for (int c = MIN_PITCH; c <= MAX_PITCH; c += step) {
                    if (c == keep) continue;

                    table[k] = c;

                    double s = scorer.applyAsDouble(table);
                    if (s <= score + 1.0E-6) continue;

                    score = s;
                    keep = c;
                    improved = true;
                }

                table[k] = keep;
            }

            if (!improved) break;
        }

        return score;
    }

    /** Источник угла: номер тика после прыжка и вертикальная скорость. */
    @FunctionalInterface
    public interface PitchFn {
        float pitch(int ticksSinceJump, double vy);
    }

    /** Готовое расписание углов и его оценка. */
    public static final class Plan {

        public final float[] pitchByTick;
        public final double speed;
        public final int cycle;
        public final double headroom;

        Plan(float[] pitchByTick, double speed, int cycle, double headroom) {
            this.pitchByTick = pitchByTick;
            this.speed = speed;
            this.cycle = cycle;
            this.headroom = headroom;
        }

        public float pitchAt(int ticksSinceJump) {
            return this.pitchByTick[Math.min(Math.max(ticksSinceJump, 0), this.pitchByTick.length - 1)];
        }

        public float minPitch() {
            float m = Float.MAX_VALUE;

            for (int i = 0; i < Math.max(1, Math.min(this.cycle, this.pitchByTick.length)); i++) {
                m = Math.min(m, this.pitchByTick[i]);
            }

            return m;
        }

        public float maxPitch() {
            float m = -Float.MAX_VALUE;

            for (int i = 0; i < Math.max(1, Math.min(this.cycle, this.pitchByTick.length)); i++) {
                m = Math.max(m, this.pitchByTick[i]);
            }

            return m;
        }
    }
}