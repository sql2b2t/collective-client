package me.collective.module.player;

import me.collective.util.sort.ContainerGeometry;
import me.collective.util.sort.SortGroupKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Грузовая логика {@link StashSorter}: что взять из домашнего сундука, что
 * несёт игрок, куда класть (включая «аварийные дома» для остатков) и учёт
 * пустых/полных сундуков. Читает и ведет карты маршрутов сортировщика.
 *
 * <p>Выделено из тела {@link StashSorter} по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика живёт здесь.
 */
final class StashSorterCargo {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private StashSorterCargo() {
    }

    static List<Integer> computeWithdrawSlots(StashSorter b, GenericContainerScreenHandler menu) {
        int containerSlots = menu.getRows() * 9;
        List<Integer> out = new ArrayList<>();

        for (int i = 0; i < containerSlots; i++) {
            ItemStack s = menu.slots.get(i).getStack();
            if (s.isEmpty()) continue;

            String key = StashSorterRouting.routeKeyOf(b, s, false);
            if (key == null || b.deadRoutes.contains(key)) continue;

            List<BlockPos> targets = b.sigTargets.get(key);
            if (targets == null) continue;
            // Из домашнего сундука ничего не берём — он и так свой
            if (targets.contains(b.currentJob == null ? null : b.currentJob.pos())) continue;
            if (destFor(b, key) == null && b.currentJob != null
                && b.currentJob.pos().equals(b.overflowChests.get(SortGroupKey.overflowKey(key)))) continue;

            out.add(i);
        }

        return out;
    }

    /** Что игрок сейчас несёт, сгруппированное по маршрутам. */
    static Map<String, List<Integer>> cargoScan(StashSorter b) {
        Map<String, List<Integer>> out = new TreeMap<>();
        if (mc.player == null) return out;

        for (int i : cargoSlots(b)) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String key = StashSorterRouting.routeKeyOf(b, s, true);
            if (key != null && !b.deadRoutes.contains(key)) out.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return out;
    }

    /** Слоты инвентаря, которые можно использовать как переноску. */
    static List<Integer> cargoSlots(StashSorter b) {
        List<Integer> out = new ArrayList<>(36);
        for (int i = b.reservedHotbarSlots.get(); i < 36; i++) {
            if (!b.keepSlots.contains(i)) out.add(i);
        }
        return out;
    }

    static int ferryFreeSlots(StashSorter b) {
        int free = 0;
        for (int i : cargoSlots(b)) if (mc.player.getInventory().getStack(i).isEmpty()) free++;
        return free;
    }

    static List<Integer> depositSlots(StashSorter b) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : cargoScan(b).entrySet()) {
            List<BlockPos> targets = b.sigTargets.get(e.getKey());
            if (e.getKey().equals(b.currentRouteKey) || (targets != null && targets.contains(b.currentDest))) {
                out.addAll(e.getValue());
            }
        }
        return out;
    }

    static int freeSlotsIn(GenericContainerScreenHandler menu) {
        int free = 0;
        int containerSlots = menu.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) if (menu.slots.get(i).getStack().isEmpty()) free++;
        return free;
    }

    static void noteIfEmptied(StashSorter b, GenericContainerScreenHandler menu, BlockPos pos) {
        int containerSlots = menu.getRows() * 9;
        for (int i = 0; i < containerSlots; i++) {
            if (!menu.slots.get(i).getStack().isEmpty()) return;
        }

        b.knownFree.put(pos, containerSlots);
        if (b.markedFull.contains(pos) || b.emptyClaims.contains(pos) || b.spareEmpties.contains(pos)) return;
        for (List<BlockPos> targets : b.sigTargets.values()) if (targets.contains(pos)) return;

        b.spareEmpties.add(pos);
    }

    static void markFull(StashSorter b, @Nullable BlockPos pos) {
        if (pos == null || mc.world == null) return;
        b.markedFull.add(pos);
        BlockPos other = ContainerGeometry.otherHalf(mc.world, pos);
        if (other != null) b.markedFull.add(other);
    }

    static void claimEmpty(StashSorter b, @Nullable BlockPos pos) {
        if (pos == null || mc.world == null) return;
        b.emptyClaims.add(pos);
        BlockPos other = ContainerGeometry.otherHalf(mc.world, pos);
        if (other != null) b.emptyClaims.add(other);
    }

    @Nullable
    static BlockPos destFor(StashSorter b, String routeKey) {
        List<BlockPos> targets = b.sigTargets.get(routeKey);
        if (targets == null) return null;
        for (BlockPos t : targets) if (!b.markedFull.contains(t)) return t;
        return null;
    }

    @Nullable
    static BlockPos huntEmptyChest(StashSorter b, String routeKey) {
        for (BlockPos p : b.unknownContainers()) {
            if (!b.markedFull.contains(p) && !b.emptyClaims.contains(p)) {
                claimEmpty(b, p);
                return p;
            }
        }
        for (BlockPos p : b.spareEmpties) {
            if (!b.markedFull.contains(p) && !b.emptyClaims.contains(p)) {
                claimEmpty(b, p);
                return p;
            }
        }
        return null;
    }

    /**
     * Аварийный дом: под остаток, которому не нашлось места, берём любой
     * свободный сундук, который ещё не закреплён за другой категорией.
     */
    @Nullable
    static BlockPos assignOverflowChest(StashSorter b, String overflowKey) {
        BlockPos existing = b.overflowChests.get(overflowKey);
        if (existing != null && !b.markedFull.contains(existing)) return existing;

        Set<BlockPos> owned = new HashSet<>();
        for (List<BlockPos> ts : b.sigTargets.values()) owned.addAll(ts);

        Set<BlockPos> queued = new HashSet<>();
        for (int i = b.jobIdx; i < b.jobs.size(); i++) queued.add(b.jobs.get(i).pos());

        Set<BlockPos> pinned = new HashSet<>();
        for (var pin : b.store().pinsFor(b.dimension())) pinned.add(ContainerGeometry.canonical(mc.world, pin.pos()));

        BlockPos best = pickOverflowChest(b, owned, queued, pinned, true);
        if (best == null) best = pickOverflowChest(b, owned, queued, pinned, false);
        if (best == null) return null;

        b.overflowChests.put(overflowKey, best);
        b.sigTargets.computeIfAbsent(overflowKey, k -> new ArrayList<>()).add(best);
        b.note("аварийный дом для «" + SortGroupKey.friendlyName(overflowKey) + "»", best.toShortString());
        return best;
    }

    @Nullable
    static BlockPos pickOverflowChest(StashSorter b, Set<BlockPos> owned, Set<BlockPos> queued, Set<BlockPos> pinned, boolean skipOwned) {
        BlockPos me = mc.player.getBlockPos();
        BlockPos best = null;
        int bestFree = 0;
        double bestDist = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, Integer> e : b.knownFree.entrySet()) {
            BlockPos pos = e.getKey();
            int free = e.getValue();
            if (free <= 0 || b.markedFull.contains(pos) || pinned.contains(pos)
                || queued.contains(pos) || b.emptyClaims.contains(pos)) continue;
            if (skipOwned && owned.contains(pos)) continue;

            double dist = me.getSquaredDistance(pos);
            // Сначала самый пустой, при равенстве — ближайший
            if (free > bestFree || (free == bestFree && (dist < bestDist || (dist == bestDist && best != null && pos.compareTo(best) < 0)))) {
                best = pos;
                bestFree = free;
                bestDist = dist;
            }
        }
        return best;
    }
}
