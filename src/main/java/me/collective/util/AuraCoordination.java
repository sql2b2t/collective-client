package me.collective.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Арбитр боевых аур (GrimAnchorAura, CrystalAura): в одном игровом тике
 * наводится и стреляет только одна аура.
 *
 * <p>Обе ауры работают через один RotationUtils-провод: сервер видит
 * единственный отправленный угол, и два одновременных заказа на разные
 * точки перезаписывают друг друга — Grim ловит дёрганье. Поэтому
 * каждая аура перед своими действиями заявляет слот тика:
 *
 * <pre>{@code
 * if (!AuraCoordination.claim(this, mc.world.getTime())) return;
 * }</pre>
 *
 * <p>Слоты раздаются честно: победитель прошлого тика пропускает очередь,
 * следующий тик отдаётся отказнику — ауры строго чередуются. Если применяется
 * одна аура, она забирает все тики, потому что список участников обновляется
 * по реальным заявкам каждый тик.
 */
public final class AuraCoordination {
    private static long tick = Long.MIN_VALUE;
    private static List<Object> lastClaimants = new ArrayList<>();
    private static List<Object> thisClaimants = new ArrayList<>();
    private static Object lastWinner;
    private static Object expected;
    private static Object winner;

    private AuraCoordination() {
    }

    /**
     * Заявка слота действия в тике.
     *
     * @param module объект-владелец (обычно this модуля)
     * @param time   монотонный счётчик тиков, {@code mc.world.getTime()}
     * @return true — тик отдан модулю, можно наводиться и отправлять пакеты
     */
    public static synchronized boolean claim(Object module, long time) {
        if (time != tick) {
            if (!thisClaimants.isEmpty()) {
                lastClaimants = new ArrayList<>(thisClaimants);
            }
            thisClaimants.clear();
            tick = time;
            winner = null;
            expected = null;
        }

        if (!thisClaimants.contains(module)) thisClaimants.add(module);

        if (winner != null) return winner == module;

        if (expected == null) {
            expected = cycleSuccessor(lastWinner, lastClaimants, module);
        }

        if (expected == module) {
            winner = module;
            lastWinner = module;
            return true;
        }

        // Тик зарезервирован за другой аурой. Если она так и не явилась,
        // список участников обновится на следующем тике и слот перепишется.
        return false;
    }

    /**
     * Победитель текущего тика — вызывает RotationUtils.claim, чтобы разрешить
     * перехват «провода» у предыдущей ауры: сервер видит в этом тике только
     * угол победителя. Для не-участников всегда false, поведение прежнее.
     */
    public static synchronized boolean isCurrentWinner(Object module) {
        return winner != null && winner == module;
    }

    /** Ближайший после прошлого победителя по кругу участников прошлого тика. */
    private static Object cycleSuccessor(Object lastWinner, List<Object> claimants, Object fallback) {
        if (claimants.isEmpty()) return fallback;
        if (claimants.size() == 1) return claimants.get(0);

        int idx = lastWinner != null ? claimants.indexOf(lastWinner) : -1;
        if (idx < 0) return fallback;

        for (int step = 1; step <= claimants.size(); step++) {
            Object candidate = claimants.get((idx + step) % claimants.size());
            if (candidate != lastWinner) return candidate;
        }
        return fallback;
    }
}
