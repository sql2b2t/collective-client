package me.collective.module.highway;

/**
 * Диспетчеризация тонких mine/place-состояний {@link HighwayBuilderHIG}:
 * каждое состояние — один вызов {@code b.mine(...)}/{@code b.place(...)} над
 * итератором позиций из {@code blockPosProvider}. Собственных полей нет —
 * вся механика в циклах билдера; константы вынесены сюда, чтобы enum-константы
 * оставались трёхстрочными оболочками.
 */
final class HighwayMinePlaceStates {
    private HighwayMinePlaceStates() {
    }

    // --- Копание ---

    static void mineFront(HighwayBuilderHIG b) {
        b.mine(b, b.blockPosProvider.getFront(), true,
            HighwayBuilderHIG.State.Forward, HighwayBuilderHIG.State.MineFront);
    }

    static void mineFloor(HighwayBuilderHIG b) {
        b.mine(b, b.blockPosProvider.getFloor(), false,
            HighwayBuilderHIG.State.Forward, HighwayBuilderHIG.State.MineFloor);
    }

    static void mineRailings(HighwayBuilderHIG b) {
        b.mine(b, b.blockPosProvider.getRailings(0), false,
            HighwayBuilderHIG.State.Forward, HighwayBuilderHIG.State.MineRailings);
    }

    static void mineAboveRailings(HighwayBuilderHIG b) {
        b.mine(b, b.blockPosProvider.getRailings(1), true,
            HighwayBuilderHIG.State.Forward, HighwayBuilderHIG.State.MineAboveRailings);
    }

    // --- Укладка ---

    static void placeCornerBlock(HighwayBuilderHIG b) {
        int slot = b.findBlocksToPlacePrioritizeTrash();
        if (slot == -1) return;

        b.place(b, b.blockPosProvider.getRailings(-1), slot, HighwayBuilderHIG.State.Forward);
    }

    static void placeRailings(HighwayBuilderHIG b) {
        int slot = b.findBlocksToPlace();
        if (slot == -1) return;

        b.place(b, b.blockPosProvider.getRailings(0), slot, HighwayBuilderHIG.State.Forward);
    }

    static void placeFloor(HighwayBuilderHIG b) {
        int slot = b.findBlocksToPlace();
        if (slot == -1) return;

        b.place(b, b.blockPosProvider.getFloor(), slot, HighwayBuilderHIG.State.Forward);
    }

    static void placeEChestBlockade(HighwayBuilderHIG b) {
        int slot = b.findBlocksToPlacePrioritizeTrash();
        if (slot == -1) return;

        b.place(b, b.blockPosProvider.getBlockade(false, b.blockadeType.getValue()), slot,
            HighwayBuilderHIG.State.MineEnderChests);
    }

    static void mineEChestBlockade(HighwayBuilderHIG b) {
        b.mine(b, b.blockPosProvider.getBlockade(true, b.blockadeType.getValue()), true,
            HighwayBuilderHIG.State.Center, HighwayBuilderHIG.State.Forward);
    }
}
