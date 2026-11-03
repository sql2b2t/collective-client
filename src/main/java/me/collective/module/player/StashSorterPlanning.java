package me.collective.module.player;

import me.collective.util.sort.ContainerGeometry;
import me.collective.util.sort.SortGroupKey;
import me.collective.util.sort.SortPlanner;
import me.collective.util.sort.SortZone;
import me.collective.util.tracker.ChestTrackerDataV2;
import me.collective.util.tracker.ChestTrackerDataManager;
import me.collective.util.tracker.TrackedContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Планировочная часть {@link StashSorter}: сборка снимков сундуков зоны для
 * {@link SortPlanner} — фильтрация по типу и зоне, свежайший дубль по канони-
 * ческому поcу, фильтр сигнатур по настройкам, свёртка мелких категорий.
 *
 * <p>Выделено из тела {@link StashSorter} по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика живёт здесь.
 */
final class StashSorterPlanning {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private StashSorterPlanning() {
    }

    static Map<String, List<BlockPos>> buildPinMap(StashSorter b, String dim) {
        Map<String, List<BlockPos>> out = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        for (var p : b.store().pinsFor(dim)) {
            BlockPos canonical = ContainerGeometry.canonical(mc.world, p.pos());
            if (seen.add(canonical)) out.computeIfAbsent(p.groupKey, k -> new ArrayList<>()).add(canonical);
        }
        return out;
    }

    static List<SortPlanner.ContainerSnapshot> buildSnapshots(StashSorter b, String dim, Set<String> pinnedKeys) {
        ChestTrackerDataV2 data = ChestTrackerDataManager.getData();
        List<SortZone> areas = b.sourceZones();
        if (areas.isEmpty()) return List.of();

        Map<BlockPos, SortPlanner.ContainerSnapshot> byCanonical = new HashMap<>();
        Map<BlockPos, Long> newest = new HashMap<>();

        for (TrackedContainer tc : data.getAllContainers(dim)) {
            String type = tc.getContainerType();
            if (!"chest".equals(type) && !"copper_chest".equals(type) && !"barrel".equals(type)) continue;

            BlockPos raw = tc.getPosition();
            BlockPos canonical = ContainerGeometry.canonical(mc.world, raw);
            if (!b.inArea(areas, raw, canonical)) continue;

            Long prev = newest.get(canonical);
            if (prev != null && prev >= tc.getLastUpdated()) continue;
            newest.put(canonical, tc.getLastUpdated());

            Map<String, Integer> sigs = new HashMap<>();
            for (Map.Entry<String, Integer> e : tc.getTopLevelSignatureCounts().entrySet()) {
                boolean shulker = SortGroupKey.isShulkerGroup(e.getKey());
                if (shulker && !b.moveShulkers.get()) continue;
                if (!shulker && b.looseItems.get() == StashSorter.LooseItems.IGNORE) continue;
                sigs.merge(e.getKey(), e.getValue(), Integer::sum);
            }

            int used = tc.getUsedStacks();
            if (used == 0 && !tc.getTopLevelSignatureCounts().isEmpty()) used = 1;

            byCanonical.put(canonical, new SortPlanner.ContainerSnapshot(
                canonical, type, slotCountOf(canonical, type, used), used, sigs));
        }

        // Сводим мелкие категории в крупные — планировщик считает уже свёрнутые
        Map<String, Integer> totals = new HashMap<>();
        for (SortPlanner.ContainerSnapshot c : byCanonical.values()) {
            for (Map.Entry<String, Integer> e : c.sigCounts().entrySet()) {
                totals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }

        Map<String, String> collapse = new HashMap<>();
        for (String key : totals.keySet()) collapse.put(key, StashSorterRouting.collapseKey(b, key, totals, pinnedKeys));
        b.keyCollapse = collapse;

        List<SortPlanner.ContainerSnapshot> out = new ArrayList<>(byCanonical.size());
        for (SortPlanner.ContainerSnapshot c : byCanonical.values()) {
            Map<String, Integer> sigs = new HashMap<>();
            for (Map.Entry<String, Integer> e : c.sigCounts().entrySet()) {
                String group = collapse.get(e.getKey());
                sigs.merge(group, StashSorterRouting.groupAmount(e.getKey(), group, e.getValue()), Integer::sum);
            }
            out.add(new SortPlanner.ContainerSnapshot(c.pos(), c.type(), c.estSlots(), c.usedSlots(), sigs));
        }

        return out;
    }

    static List<SortPlanner.ContainerSnapshot> withProbeSnapshots(StashSorter b, List<SortPlanner.ContainerSnapshot> indexed, List<BlockPos> unknown) {
        if (unknown.isEmpty()) return indexed;

        List<SortPlanner.ContainerSnapshot> out = new ArrayList<>(indexed.size() + unknown.size());
        out.addAll(indexed);
        for (BlockPos p : unknown) {
            String type = b.containerTypeAt(p);
            out.add(new SortPlanner.ContainerSnapshot(p, type, slotCountOf(p, type, 0), 0, Map.of()));
        }
        return out;
    }

    static int slotCountOf(BlockPos canonical, String type, int used) {
        return ContainerGeometry.slotCount(mc.world, canonical, type, used);
    }
}
