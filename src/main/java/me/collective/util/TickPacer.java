package me.collective.util;

/**
 * Ограничитель частоты действий внутри одного тика.
 *
 * <p>Следит за тем, сколько тиков клиента попало в последние 60 мс: при
 * «зажатых» тиках (лаг, интеграция) ограничивает работу, а накопленные
 * действия отдаёт через {@link #takeDeferred()}.
 *
 * <p>Ограничение действует только когда {@link #arm()#isArmed()} — то есть
 * когда включён режим catch-up в настройках.
 *
 * <p>Перенос {@code bep.hax.util.TickPacer}, GPL-3.0.
 */
public final class TickPacer {

    private static final int MAX_TICKS = 3;
    private static final long WINDOW_NANOS = 60_000_000L;
    private static final long ARM_NANOS = 10_000_000_000L;

    private static final long[] recent = new long[MAX_TICKS];
    private static int head;
    private static long armedUntil;
    private static int deferred;
    private static int clientTicks;

    static {
        armedUntil = System.nanoTime();
    }

    private TickPacer() {
    }

    public static void arm() {
        armedUntil = System.nanoTime() + ARM_NANOS;
    }

    public static boolean isArmed() {
        return System.nanoTime() - armedUntil < 0L;
    }

    public static void onTick() {
        clientTicks++;
        recent[head] = System.nanoTime();
        head = (head + 1) % MAX_TICKS;
    }

    public static int clientTick() {
        return clientTicks;
    }

    /** Сколько действий из желаемых разрешено сейчас; остальное откладывается. */
    public static int allow(int wanted) {
        if (wanted <= 0 || !isArmed()) return wanted;

        long now = System.nanoTime();
        int inWindow = 0;

        for (long t : recent) {
            if (t == 0L || now - t >= WINDOW_NANOS) continue;
            inWindow++;
        }

        int allowed = Math.max(0, Math.min(wanted, MAX_TICKS - inWindow));
        deferred += wanted - allowed;
        return allowed;
    }

    public static int takeDeferred() {
        int d = deferred;
        deferred = 0;
        return d;
    }
}