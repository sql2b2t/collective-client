package me.collective.gui.clickgui2;

/**
 * Микро-либа анимаций для второго стиля. Все значения считаются от
 * реального кадра (dt в секундах) — скорость не зависит от FPS.
 */
public final class ClickGui2Anim {
    private ClickGui2Anim() {
    }

    /** Экспоненциальное сглаживание к цели; speed — «полупериод» в 1/сек. */
    public static float to(float current, float target, float dt, float speed) {
        float t = 1f - (float) Math.exp(-speed * dt);
        return current + (target - current) * t;
    }

    /** Так же, но для значений 0..1 с автоматическим снапом к границам. */
    public static float to01(float current, boolean toOne, float dt, float speed) {
        float v = to(current, toOne ? 1f : 0f, dt, speed);
        if (v < 0.002f) v = 0f;
        if (v > 0.998f) v = 1f;
        return v;
    }

    /** Плавное взлёт-образное появление: 0 → 1 по easeOutCubic. */
    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float t1 = 1f - t;
        return 1f - t1 * t1 * t1;
    }

    /** Винтовое easing исходника: 1 − e^(−8t)(cos 8t + sin 8t) — лёгкий overdrive. */
    public static float spring(float t) {
        t = Math.max(0f, Math.min(1f, t));
        double k = t * 8.0;
        return (float) (1.0 - Math.exp(-k) * (Math.cos(k) + Math.sin(k)));
    }

    /** Шаг с задержкой по индексу (pos) внутри общего прогресса t: появление каскадом. */
    public static float stagger(float t, int pos, int total) {
        if (t >= 1f) return 1f;
        float slot = total <= 1 ? 0f : pos / (float) (total - 1);
        float local = (t - slot * 0.35f) / Math.max(0.05f, 1f - slot * 0.35f);
        return easeOut(local);
    }

    /** Смягчение s-кривой (мгновенная альфа твина). */
    public static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /** Мигание каретки: 0.5 секунды виден, 0.5 — нет. */
    public static boolean caretBlink() {
        return System.currentTimeMillis() / 500L % 2L == 0L;
    }
}
