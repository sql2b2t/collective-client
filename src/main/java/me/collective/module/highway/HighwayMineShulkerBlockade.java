package me.collective.module.highway;

/**
 * Логика состояния MineShulkerBlockade билдера: добывает установленный
 * шалкер-блокадник, после повторного входа в состояние выдерживает
 * 12-тиковую паузу перед переходом к выбросу мусора.
 *
 * <p>Выделено из тела константы {@code State.MineShulkerBlockade} по паттерну
 * {@link HighwayRestock}: константа остаётся тонкой оболочкой, состояние и
 * логика живут здесь (один экземпляр на JVM — как и прежнее тело константы).
 */
final class HighwayMineShulkerBlockade {
    static final HighwayMineShulkerBlockade INSTANCE = new HighwayMineShulkerBlockade();

    private boolean stopTimerEnabled;
    private int stopTimer;

    private HighwayMineShulkerBlockade() {
    }

    void start(HighwayBuilderHIG b) {
        stopTimerEnabled = false;
        if (b.lastState == HighwayBuilderHIG.State.MineShulkerBlockade) {
            stopTimerEnabled = true;
            stopTimer = 12;
        }
    }

    void tick(HighwayBuilderHIG b) {
        if (!stopTimerEnabled) {
            b.mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.getValue()), true,
                HighwayBuilderHIG.State.MineShulkerBlockade, HighwayBuilderHIG.State.MineShulkerBlockade);
        } else {
            stopTimer--;
            if (stopTimer <= 0) {
                b.setState(HighwayBuilderHIG.State.ThrowOutTrash, HighwayBuilderHIG.State.Forward);
            }
        }
    }
}
