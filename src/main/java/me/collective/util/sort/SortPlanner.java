package me.collective.util.sort;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

/**
 * Решает, что и куда положить.
 *
 * <p>Идея: у каждой категории вещей («сигнатуры») должен быть свой адрес.
 * Планировщик раздаёт категориям сундуки, считает, сколько всего надо
 * переложить и хватит ли места, и выдаёт список заданий вида
 * «в этом сундуке забери эти категории» — от ближайшего к игроку.
 *
 * <p>Порядок выдачи сундуков важен: сначала те, что игрок сам отметил
 * (пины), потом свободные рядом, и только потом — остальные хранители
 * той же категории, чтобы не плодить одинаковое по разным сундукам.
 */
public final class SortPlanner {
    private SortPlanner() {
    }

    /**
     * @param all              снимки всех контейнеров в зоне
     * @param minSigCount      меньше этого количества категория не сортируется вовсе
     * @param origin           игрок: отсюда считаем расстояния
     * @param maxStackOf       максимальный размер стопки для категории
     * @param pins             категория → сундуки, которые игрок назначил этой категории
     * @param minForOwnChest   с какого количества категория заслуживает свой сундук
     * @param probeCandidates  сундуки без записи в индексе — их можно занять
     */
    public static Plan planConsolidation(
        List<ContainerSnapshot> all,
        int minSigCount,
        BlockPos origin,
        ToIntFunction<String> maxStackOf,
        Map<String, List<BlockPos>> pins,
        int minForOwnChest,
        List<BlockPos> probeCandidates
    ) {
        List<String> warnings = new ArrayList<>();

        // Кто сколько чего держит: категория → (сундук → количество)
        Map<String, Map<BlockPos, Integer>> holders = new TreeMap<>();
        for (ContainerSnapshot c : all) {
            for (Entry<String, Integer> e : c.sigCounts().entrySet()) {
                if (e.getValue() > 0) holders.computeIfAbsent(e.getKey(), k -> new TreeMap<>()).merge(c.pos(), e.getValue(), Integer::sum);
            }
        }

        Map<BlockPos, Integer> freeSlots = new HashMap<>();
        for (ContainerSnapshot c : all) freeSlots.put(c.pos(), Math.max(0, c.estSlots() - c.usedSlots()));

        Map<BlockPos, String> pinOwner = new HashMap<>();
        for (Entry<String, List<BlockPos>> e : pins.entrySet()) {
            for (BlockPos p : e.getValue()) pinOwner.putIfAbsent(p, e.getKey());
        }

        // «Хозяин» сундука: пин, иначе — то, чего в нём больше всего
        Map<BlockPos, String> resident = new HashMap<>(pinOwner);
        for (ContainerSnapshot c : all) {
            if (resident.containsKey(c.pos())) continue;
            String best = null;
            int bestCount = 0;
            for (Entry<String, Integer> e : new TreeMap<>(c.sigCounts()).entrySet()) {
                if (e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            if (best != null) resident.put(c.pos(), best);
        }

        TreeSet<BlockPos> probePool = new TreeSet<>();
        for (BlockPos p : probeCandidates) if (!pinOwner.containsKey(p)) probePool.add(p);

        TreeSet<BlockPos> emptyPool = new TreeSet<>();
        for (ContainerSnapshot c : all) {
            if (c.usedSlots() == 0 && !pinOwner.containsKey(c.pos())) emptyPool.add(c.pos());
        }

        Set<BlockPos> claimedEmpties = new TreeSet<>();
        TreeSet<BlockPos> claimable = new TreeSet<>(probePool);
        claimable.addAll(emptyPool);
        int claimableAtStart = claimable.size();

        Map<String, List<BlockPos>> sigTargets = new LinkedHashMap<>();
        Map<BlockPos, Set<String>> withdrawBySource = new HashMap<>();

        Set<String> allKeys = new TreeSet<>(holders.keySet());
        allKeys.addAll(pins.keySet());

        Map<String, Integer> totals = new HashMap<>();
        for (Entry<String, Map<BlockPos, Integer>> e : holders.entrySet()) {
            totals.put(e.getKey(), e.getValue().values().stream().mapToInt(Integer::intValue).sum());
        }

        // Большие категории получают сундуки первыми — их некуда деть
        List<String> keys = new ArrayList<>(allKeys);
        keys.sort(Comparator.<String>comparingInt(k -> -totals.getOrDefault(k, 0)).thenComparing(k -> k));

        List<Kind> kinds = new ArrayList<>();
        for (String sig : keys) {
            Map<BlockPos, Integer> perContainer = holders.getOrDefault(sig, Map.of());
            List<BlockPos> pinned = pins.getOrDefault(sig, List.of());
            int total = totals.getOrDefault(sig, 0);

            if (pinned.isEmpty()) {
                if (total < minSigCount) continue;

                if (perContainer.size() == 1) {
                    BlockPos only = perContainer.keySet().iterator().next();
                    // Уже всё на месте: одна категория — один сундук
                    if (sig.equals(resident.get(only))) {
                        sigTargets.put(sig, new ArrayList<>(List.of(only)));
                        continue;
                    }
                    if (!exemptFromOwnChestMin(sig) && total < minForOwnChest) continue;
                }
            }

            List<BlockPos> targets = new ArrayList<>(pinned);
            Set<BlockPos> targetSet = new TreeSet<>(targets);
            List<BlockPos> residentChests = new ArrayList<>();
            List<BlockPos> otherHolders = new ArrayList<>();

            for (BlockPos p : perContainer.keySet()) {
                if (targetSet.contains(p)) continue;
                String owner = pinOwner.get(p);
                if (owner == null || owner.equals(sig)) {
                    if (sig.equals(resident.get(p))) residentChests.add(p);
                    else otherHolders.add(p);
                }
            }

            Comparator<BlockPos> byCount = Comparator.<BlockPos>comparingInt(px -> -perContainer.get(px)).thenComparing(px -> px);
            residentChests.sort(byCount);
            otherHolders.sort(byCount);

            int maxStack = Math.max(1, maxStackOf.applyAsInt(sig));
            int capacity = 0;
            for (BlockPos p : targets) capacity += freeSlots.getOrDefault(p, 27) * maxStack;

            int movable = total;
            int holdersInTargets = 0;
            for (BlockPos p : perContainer.keySet()) if (targetSet.contains(p)) holdersInTargets++;
            for (BlockPos p : targets) movable -= perContainer.getOrDefault(p, 0);

            Kind kind = new Kind(sig, total, maxStack, perContainer, targets, targetSet, otherHolders,
                !pinned.isEmpty() || exemptFromOwnChestMin(sig) || total >= minForOwnChest,
                !targets.isEmpty() ? targets.get(0) : (!otherHolders.isEmpty() ? otherHolders.get(0) : origin));
            kind.capacity = capacity;
            kind.movable = movable;
            kind.holdersInTargets = holdersInTargets;

            // Мест не хватило — занимаем сундуки, где эта категория и так главная.
            // Один сундук всегда оставляем нетронутым, иначе вещи некуда класть.
            for (BlockPos candidate : residentChests) {
                boolean needMore = kind.targets.isEmpty() || kind.capacity < kind.movable;
                boolean notAllClaimed = perContainer.isEmpty() || kind.holdersInTargets < perContainer.size() - 1;
                if (!needMore || !notAllClaimed) break;

                kind.targets.add(candidate);
                kind.targetSet.add(candidate);
                kind.holdersInTargets++;
                kind.capacity += freeSlots.getOrDefault(candidate, 0) * maxStack;
                kind.movable -= perContainer.getOrDefault(candidate, 0);
            }

            kinds.add(kind);
        }

        // Категории, которым положен свой сундук, но он не назначен
        for (Kind k : kinds) {
            if (probePool.isEmpty() && emptyPool.isEmpty()) break;
            if (k.entitled && k.targets.isEmpty()) claim(k, probePool, emptyPool, claimedEmpties, freeSlots);
        }

        // Оставшиеся пустые сундуки — самому нуждающемуся
        while (!probePool.isEmpty() || !emptyPool.isEmpty()) {
            Kind neediest = null;
            int worst = 0;
            for (Kind k : kinds) {
                if (k.entitled && k.deficitSlots() > worst) {
                    worst = k.deficitSlots();
                    neediest = k;
                }
            }
            if (neediest == null) break;
            claim(neediest, probePool, emptyPool, claimedEmpties, freeSlots);
        }

        List<String> homeless = new ArrayList<>();
        List<String> tight = new ArrayList<>();

        for (Kind k : kinds) {
            // Добор остальных хранителей: это и есть «свести всё в один сундук»
            for (BlockPos candidate : k.otherHolders) {
                boolean full = !k.targets.isEmpty() && k.capacity >= k.movable;
                boolean notAllClaimed = !k.perContainer.isEmpty() && k.holdersInTargets < k.perContainer.size() - 1;
                if (full || notAllClaimed) break;

                k.targets.add(candidate);
                k.targetSet.add(candidate);
                k.holdersInTargets++;
                k.capacity += freeSlots.getOrDefault(candidate, 0) * k.maxStack;
                k.movable -= k.perContainer.getOrDefault(candidate, 0);
            }

            if (k.targets.isEmpty()) {
                homeless.add(SortGroupKey.friendlyName(k.sig));
            } else {
                if (k.capacity < k.movable) tight.add(SortGroupKey.friendlyName(k.sig));
                sigTargets.put(k.sig, k.targets);

                // Из всех остальных хранителей эту категорию надо забрать
                for (BlockPos holder : k.perContainer.keySet()) {
                    if (!k.targetSet.contains(holder)) {
                        withdrawBySource.computeIfAbsent(holder, x -> new TreeSet<>()).add(k.sig);
                    }
                }
            }
        }

        if (!homeless.isEmpty()) {
            warnings.add(homeless.size() + " категорий без сундука: " + summarize(homeless)
                + ". Все " + claimableAtStart + " пустых и неизвестных сундуков в зоне уже заняты — расширь зону на пустые сундуки, добавь их или подними «минимальный размер категории».");
        }
        if (!tight.isEmpty()) {
            warnings.add(tight.size() + " категорий не влезут целиком, лягут только до края: " + summarize(tight) + ".");
        }

        // Ближний сундук разбираем первым: меньше ходьбы
        List<SweepJob> jobs = new ArrayList<>();
        for (ContainerSnapshot c : byDistance(all, origin)) {
            Set<String> sigs = withdrawBySource.get(c.pos());
            if (sigs != null && !sigs.isEmpty()) jobs.add(new SweepJob(c.pos(), sigs));
        }

        return new Plan(jobs, sigTargets, claimedEmpties, warnings);
    }

    private static void claim(Kind kind, TreeSet<BlockPos> probePool, TreeSet<BlockPos> emptyPool,
        Set<BlockPos> claimedEmpties, Map<BlockPos, Integer> freeSlots) {
        // Сначала сундуки без записи в индексе: их содержимое неизвестно,
        // зато они точно не нужны под что-то другое
        BlockPos claim = nearest(probePool.isEmpty() ? emptyPool : probePool, kind.anchor);
        if (claim == null) return;

        probePool.remove(claim);
        emptyPool.remove(claim);
        claimedEmpties.add(claim);
        kind.targets.add(claim);
        kind.targetSet.add(claim);
        kind.capacity += freeSlots.getOrDefault(claim, 27) * kind.maxStack;
    }

    /** Шалкеры и смешанное не ждут «своего» сундука — их иначе не разобрать. */
    private static boolean exemptFromOwnChestMin(String sig) {
        return SortGroupKey.isShulkerKey(sig) || SortGroupKey.MIXED_ITEMS.equals(sig);
    }

    private static String summarize(List<String> names) {
        int shown = Math.min(3, names.size());
        String head = String.join(", ", names.subList(0, shown));
        return names.size() > shown ? head + ", ещё " + (names.size() - shown) : head;
    }

    private static BlockPos nearest(TreeSet<BlockPos> pool, BlockPos anchor) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : pool) {
            double d = anchor.getSquaredDistance(p);
            if (d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    private static List<ContainerSnapshot> byDistance(List<ContainerSnapshot> containers, BlockPos origin) {
        List<ContainerSnapshot> ordered = new ArrayList<>(containers);
        ordered.sort(Comparator.<ContainerSnapshot>comparingDouble(c -> origin.getSquaredDistance(c.pos())).thenComparing(ContainerSnapshot::pos));
        return ordered;
    }

    /** Снимок контейнера на момент планирования. */
    public record ContainerSnapshot(BlockPos pos, String type, int estSlots, int usedSlots, Map<String, Integer> sigCounts) {
    }

    /** Промежуточное состояние: категория и её назначенные сундуки. */
    private static final class Kind {
        final String sig;
        final int total;
        final int maxStack;
        final Map<BlockPos, Integer> perContainer;
        final List<BlockPos> targets;
        final Set<BlockPos> targetSet;
        final List<BlockPos> otherHolders;
        final boolean entitled;
        final BlockPos anchor;
        int capacity;
        int movable;
        int holdersInTargets;

        Kind(String sig, int total, int maxStack, Map<BlockPos, Integer> perContainer, List<BlockPos> targets,
            Set<BlockPos> targetSet, List<BlockPos> otherHolders, boolean entitled, BlockPos anchor) {
            this.sig = sig;
            this.total = total;
            this.maxStack = maxStack;
            this.perContainer = perContainer;
            this.targets = targets;
            this.targetSet = targetSet;
            this.otherHolders = otherHolders;
            this.entitled = entitled;
            this.anchor = anchor;
        }

        /** Сколько ещё слотов не хватает этой категории. */
        int deficitSlots() {
            int items = movable - capacity;
            return items <= 0 ? 0 : (items + maxStack - 1) / maxStack;
        }
    }

    /** Готовый план: задания, назначения и предупреждения. */
    public record Plan(List<SweepJob> jobs, Map<String, List<BlockPos>> sigTargets, Set<BlockPos> claimedEmpties, List<String> warnings) {
    }

    /** «В этом сундуке забери вот эти категории». */
    public record SweepJob(BlockPos pos, Set<String> withdrawSigs) {
    }
}