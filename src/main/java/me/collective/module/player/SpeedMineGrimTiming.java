package me.collective.module.player;

import me.collective.util.SwapManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Grim-тайминги и «бюджеты» пакетов для {@link SpeedMine}: баланс задержек
 * стартовых/финальных dig-пакетов против оценки сервера и rate-limit
 * bypass-стартов (завышенный Y для лёгких позиций).
 *
 * <p>Выделено из тела {@link SpeedMine} по паттерну компаньонов: зеркала
 * баланса и окна пакетов живут здесь; модуль держит тонкие делегаты.
 * Константы переехали вместе с логикой — в теле модуля они дублировались
 * литералами (275L/300L/700.0/800.0/25.0/955/2000/40), теперь расхождение
 * невозможно.
 */
final class SpeedMineGrimTiming {
    /** Минимальная пауза между завершённым копанием и новым стартом, мс. */
    static final long GRIM_START_GAP_MS = 275L;
    /** «Полная» пауза, после которой бюджет не растёт. */
    static final long GRIM_START_FULL_MS = 300L;
    /** Допуск расхождения предсказанного и реального времени копания, мс. */
    static final double GRIM_BREAK_SLACK_MS = 25.0;
    /** Бюджет задержек стартов, мс. */
    static final double DELAY_BUDGET_MS = 700.0;
    /** Бюджет задержек завершений, мс. */
    static final double BREAK_BUDGET_MS = 800.0;
    /** Мягкий потолок накопленного дисбаланса, мс. */
    static final double GRIM_FLAG_MS = 1000.0;
    /** Bypass-старт: сдвиг Y вверх, чтобы сервер не связал dig с реальным блоком. */
    static final int BYPASS_Y_OFFSET = 955;
    static final int BYPASS_Y_MAX = 2000;
    /** Rate-limit bypass-стартов, пакетов в секунду. */
    static final int MAX_BYPASS_DIGS_PER_SECOND = 40;

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private long lastFinishMs;
    private long grimStartMs;
    /** Пишется из noteStartSent и из rebreak-обработчика модуля. */
    boolean grimSampleIsAir;
    /** Читается модулем для предупреждения о дисбалансе. */
    double delayBalanceMirror;
    private double breakBalanceMirror;
    private final java.util.ArrayDeque<Long> bypassPacketTimes = new java.util.ArrayDeque<>();

    SpeedMineGrimTiming() {
    }

    /** Полный сброс зеркал (отключение модуля / смена мира). */
    void reset() {
        lastFinishMs = 0L;
        grimStartMs = 0L;
        grimSampleIsAir = false;
        delayBalanceMirror = 0.0;
        breakBalanceMirror = 0.0;
        bypassPacketTimes.clear();
    }

    long sinceFinish() {
        return lastFinishMs == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastFinishMs;
    }

    void noteStartSent(SpeedMine b, boolean airSample) {
        long since = sinceFinish();
        if (since >= GRIM_START_GAP_MS) {
            delayBalanceMirror *= 0.9;
        } else {
            delayBalanceMirror = Math.min(GRIM_FLAG_MS, delayBalanceMirror + (GRIM_START_FULL_MS - since));
        }

        grimStartMs = System.currentTimeMillis();
        grimSampleIsAir = airSample;
    }

    void noteFinishSent(double predictedMs) {
        long now = System.currentTimeMillis();
        double predicted = grimSampleIsAir ? 0.0 : predictedMs;
        double real = grimStartMs == 0L ? 0.0 : now - grimStartMs;
        breakBalanceMirror = MathHelper.clamp(breakBalanceMirror, -GRIM_FLAG_MS, GRIM_FLAG_MS);
        double diff = predicted - real;
        if (diff < GRIM_BREAK_SLACK_MS) {
            breakBalanceMirror *= 0.9;
        } else {
            breakBalanceMirror += diff;
        }

        lastFinishMs = now;
        grimStartMs = now;
    }

    boolean startBudgetOk() {
        long since = sinceFinish();
        return since >= GRIM_START_GAP_MS || delayBalanceMirror + (GRIM_START_FULL_MS - since) <= DELAY_BUDGET_MS;
    }

    boolean finishBudgetOk(double predictedMs) {
        if (grimSampleIsAir) {
            return true;
        }

        double diff = predictedMs - (grimStartMs == 0L ? 0L : System.currentTimeMillis() - grimStartMs);
        return diff < GRIM_BREAK_SLACK_MS || breakBalanceMirror + diff <= BREAK_BUDGET_MS;
    }

    static double grimPredictedMs(SpeedMine.MiningData data) {
        return grimPredictedFromDelta(data.grimMaxDelta);
    }

    static double grimPredictedFromDelta(float delta) {
        return delta <= 0.0F ? GRIM_FLAG_MS : Math.ceil(1.0 / Math.min(1.0F, delta)) * 50.0;
    }

    void sendBypassStart(SpeedMine b, BlockPos pos) {
        int y = Math.min(BYPASS_Y_MAX, pos.getY() + BYPASS_Y_OFFSET);
        bypassPacketTimes.addLast(System.currentTimeMillis());
        b.sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(pos.getX(), y, pos.getZ()), Direction.DOWN);
        noteStartSent(b, true);
    }

    boolean bypassBudgetAvailable() {
        long now = System.currentTimeMillis();

        while (!bypassPacketTimes.isEmpty() && now - bypassPacketTimes.peekFirst() > (long) GRIM_FLAG_MS) {
            bypassPacketTimes.pollFirst();
        }

        return bypassPacketTimes.size() < MAX_BYPASS_DIGS_PER_SECOND;
    }

    boolean shouldBypass(SpeedMine b, double predictedMs) {
        if (b.grimBypassConfig.get() != SpeedMine.GrimBypass.OFF && !grimSampleIsAir) {
            if (sinceFinish() < GRIM_START_GAP_MS) {
                return false;
            } else {
                return !bypassBudgetAvailable() ? false
                    : b.grimBypassConfig.get() == SpeedMine.GrimBypass.ALWAYS || !finishBudgetOk(predictedMs);
            }
        } else {
            return false;
        }
    }
}
