package me.collective.client.utils.sort

import net.minecraft.core.BlockPos
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.TreeMap
import java.util.TreeSet
import java.util.function.ToIntFunction

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
class SortPlanner private constructor() {
    companion object {
        /**
         * @param all              снимки всех контейнеров в зоне
         * @param minSigCount      меньше этого количества категория не сортируется вовсе
         * @param origin           игрок: отсюда считаем расстояния
         * @param maxStackOf       максимальный размер стопки для категории
         * @param pins             категория → сундуки, которые игрок назначил этой категории
         * @param minForOwnChest   с какого количества категория заслуживает свой сундук
         * @param probeCandidates  сундуки без записи в индексе — их можно занять
         */
        @JvmStatic
        fun planConsolidation(
            all: List<ContainerSnapshot>,
            minSigCount: Int,
            origin: BlockPos,
            maxStackOf: ToIntFunction<String>,
            pins: Map<String, List<BlockPos>>,
            minForOwnChest: Int,
            probeCandidates: List<BlockPos>
        ): Plan {
            val warnings = ArrayList<String>()

            // Кто сколько чего держит: категория → (сундук → количество)
            val holders = TreeMap<String, MutableMap<BlockPos, Int>>()
            for (c in all) {
                for (e in c.sigCounts.entries) {
                    if (e.value > 0) holders.computeIfAbsent(e.key) { TreeMap() }.merge(c.pos, e.value) { a, b -> a + b }
                }
            }

            val freeSlots = HashMap<BlockPos, Int>()
            for (c in all) freeSlots[c.pos] = maxOf(0, c.estSlots - c.usedSlots)

            val pinOwner = HashMap<BlockPos, String>()
            for (e in pins.entries) {
                for (p in e.value) pinOwner.putIfAbsent(p, e.key)
            }

            // «Хозяин» сундука: пин, иначе — то, чего в нём больше всего
            val resident = HashMap(pinOwner)
            for (c in all) {
                if (resident.containsKey(c.pos)) continue
                var best: String? = null
                var bestCount = 0
                for (e in TreeMap(c.sigCounts).entries) {
                    if (e.value > bestCount) {
                        best = e.key
                        bestCount = e.value
                    }
                }
                if (best != null) resident[c.pos] = best
            }

            val probePool = TreeSet<BlockPos>()
            for (p in probeCandidates) if (!pinOwner.containsKey(p)) probePool.add(p)

            val emptyPool = TreeSet<BlockPos>()
            for (c in all) {
                if (c.usedSlots == 0 && !pinOwner.containsKey(c.pos)) emptyPool.add(c.pos)
            }

            val claimedEmpties = TreeSet<BlockPos>()
            val claimable = TreeSet(probePool)
            claimable.addAll(emptyPool)
            val claimableAtStart = claimable.size

            val sigTargets = LinkedHashMap<String, List<BlockPos>>()
            val withdrawBySource = HashMap<BlockPos, MutableSet<String>>()

            val allKeys = TreeSet(holders.keys)
            allKeys.addAll(pins.keys)

            val totals = HashMap<String, Int>()
            for (e in holders.entries) {
                totals[e.key] = e.value.values.sum()
            }

            // Большие категории получают сундуки первыми — их некуда деть
            val keys = ArrayList(allKeys)
            keys.sortWith(compareBy<String>({ -(totals.getOrDefault(it, 0)) }, { it }))

            val kinds = ArrayList<Kind>()
            for (sig in keys) {
                val perContainer: Map<BlockPos, Int> = holders.getOrDefault(sig, emptyMap())
                val pinned: List<BlockPos> = pins.getOrDefault(sig, emptyList())
                val total = totals.getOrDefault(sig, 0)

                if (pinned.isEmpty()) {
                    if (total < minSigCount) continue

                    if (perContainer.size == 1) {
                        val only = perContainer.keys.iterator().next()
                        // Уже всё на месте: одна категория — один сундук
                        if (sig == resident[only]) {
                            sigTargets[sig] = ArrayList(listOf(only))
                            continue
                        }
                        if (!exemptFromOwnChestMin(sig) && total < minForOwnChest) continue
                    }
                }

                val targets = ArrayList(pinned)
                val targetSet = TreeSet(targets)
                val residentChests = ArrayList<BlockPos>()
                val otherHolders = ArrayList<BlockPos>()

                for (p in perContainer.keys) {
                    if (targetSet.contains(p)) continue
                    val owner = pinOwner[p]
                    if (owner == null || owner == sig) {
                        if (sig == resident[p]) residentChests.add(p)
                        else otherHolders.add(p)
                    }
                }

                val byCount = compareBy<BlockPos>({ -(perContainer[it]!!) }, { it })
                residentChests.sortWith(byCount)
                otherHolders.sortWith(byCount)

                val maxStack = maxOf(1, maxStackOf.applyAsInt(sig))
                var capacity = 0
                for (p in targets) capacity += freeSlots.getOrDefault(p, 27) * maxStack

                var movable = total
                var holdersInTargets = 0
                for (p in perContainer.keys) if (targetSet.contains(p)) holdersInTargets++
                for (p in targets) movable -= perContainer.getOrDefault(p, 0)

                val kind = Kind(sig, total, maxStack, perContainer, targets, targetSet, otherHolders,
                    pinned.isNotEmpty() || exemptFromOwnChestMin(sig) || total >= minForOwnChest,
                    if (targets.isNotEmpty()) targets[0] else (if (otherHolders.isNotEmpty()) otherHolders[0] else origin))
                kind.capacity = capacity
                kind.movable = movable
                kind.holdersInTargets = holdersInTargets

                // Мест не хватило — занимаем сундуки, где эта категория и так главная.
                // Один сундук всегда оставляем нетронутым, иначе вещи некуда класть.
                for (candidate in residentChests) {
                    val needMore = kind.targets.isEmpty() || kind.capacity < kind.movable
                    val notAllClaimed = perContainer.isEmpty() || kind.holdersInTargets < perContainer.size - 1
                    if (!needMore || !notAllClaimed) break

                    kind.targets.add(candidate)
                    kind.targetSet.add(candidate)
                    kind.holdersInTargets++
                    kind.capacity += freeSlots.getOrDefault(candidate, 0) * maxStack
                    kind.movable -= perContainer.getOrDefault(candidate, 0)
                }

                kinds.add(kind)
            }

            // Категории, которым положен свой сундук, но он не назначен
            for (k in kinds) {
                if (probePool.isEmpty() && emptyPool.isEmpty()) break
                if (k.entitled && k.targets.isEmpty()) claim(k, probePool, emptyPool, claimedEmpties, freeSlots)
            }

            // Оставшиеся пустые сундуки — самому нуждающемуся
            while (probePool.isNotEmpty() || emptyPool.isNotEmpty()) {
                var neediest: Kind? = null
                var worst = 0
                for (k in kinds) {
                    if (k.entitled && k.deficitSlots() > worst) {
                        worst = k.deficitSlots()
                        neediest = k
                    }
                }
                if (neediest == null) break
                claim(neediest, probePool, emptyPool, claimedEmpties, freeSlots)
            }

            val homeless = ArrayList<String>()
            val tight = ArrayList<String>()

            for (k in kinds) {
                // Добор остальных хранителей: это и есть «свести всё в один сундук»
                for (candidate in k.otherHolders) {
                    val full = k.targets.isNotEmpty() && k.capacity >= k.movable
                    val notAllClaimed = k.perContainer.isNotEmpty() && k.holdersInTargets < k.perContainer.size - 1
                    if (full || notAllClaimed) break

                    k.targets.add(candidate)
                    k.targetSet.add(candidate)
                    k.holdersInTargets++
                    k.capacity += freeSlots.getOrDefault(candidate, 0) * k.maxStack
                    k.movable -= k.perContainer.getOrDefault(candidate, 0)
                }

                if (k.targets.isEmpty()) {
                    homeless.add(SortGroupKey.friendlyName(k.sig))
                } else {
                    if (k.capacity < k.movable) tight.add(SortGroupKey.friendlyName(k.sig))
                    sigTargets[k.sig] = k.targets

                    // Из всех остальных хранителей эту категорию надо забрать
                    for (holder in k.perContainer.keys) {
                        if (!k.targetSet.contains(holder)) {
                            withdrawBySource.computeIfAbsent(holder) { TreeSet() }.add(k.sig)
                        }
                    }
                }
            }

            if (homeless.isNotEmpty()) {
                warnings.add(homeless.size.toString() + " категорий без сундука: " + summarize(homeless) +
         ". Все " + claimableAtStart + " пустых и неизвестных сундуков в зоне уже заняты — расширь зону на пустые сундуки, добавь их или подними «минимальный размер категории».")
            }
            if (tight.isNotEmpty()) {
                warnings.add(tight.size.toString() + " категорий не влезут целиком, лягут только до края: " + summarize(tight) + ".")
            }

            // Ближний сундук разбираем первым: меньше ходьбы
            val jobs = ArrayList<SweepJob>()
            for (c in byDistance(all, origin)) {
                val sigs = withdrawBySource[c.pos]
                if (sigs != null && sigs.isNotEmpty()) jobs.add(SweepJob(c.pos, sigs))
            }

            return Plan(jobs, sigTargets, claimedEmpties, warnings)
        }

        private fun claim(kind: Kind, probePool: TreeSet<BlockPos>, emptyPool: TreeSet<BlockPos>,
            claimedEmpties: MutableSet<BlockPos>, freeSlots: Map<BlockPos, Int>) {
            // Сначала сундуки без записи в индексе: их содержимое неизвестно,
            // зато они точно не нужны под что-то другое
            val pos = nearest(if (probePool.isEmpty()) emptyPool else probePool, kind.anchor) ?: return

            probePool.remove(pos)
            emptyPool.remove(pos)
            claimedEmpties.add(pos)
            kind.targets.add(pos)
            kind.targetSet.add(pos)
            kind.capacity += freeSlots.getOrDefault(pos, 27) * kind.maxStack
        }

        /** Шалкеры и смешанное не ждут «своего» сундука — их иначе не разобрать. */
        private fun exemptFromOwnChestMin(sig: String): Boolean {
            return SortGroupKey.isShulkerKey(sig) || SortGroupKey.MIXED_ITEMS == sig
        }

        private fun summarize(names: List<String>): String {
            val shown = minOf(3, names.size)
            val head = names.subList(0, shown).joinToString(", ")
            return if (names.size > shown) head + ", ещё " + (names.size - shown) else head
        }

        private fun nearest(pool: TreeSet<BlockPos>, anchor: BlockPos): BlockPos? {
            var best: BlockPos? = null
            var bestDist = Double.MAX_VALUE
            for (p in pool) {
                val d = anchor.distSqr(p)
                if (d < bestDist) {
                    best = p
                    bestDist = d
                }
            }
            return best
        }

        private fun byDistance(containers: List<ContainerSnapshot>, origin: BlockPos): List<ContainerSnapshot> {
            val ordered = ArrayList(containers)
            ordered.sortWith(compareBy<ContainerSnapshot>({ origin.distSqr(it.pos) }, { it.pos }))
            return ordered
        }
    }

    /** Снимок контейнера на момент планирования. */
    data class ContainerSnapshot(val pos: BlockPos, val type: String, val estSlots: Int, val usedSlots: Int, val sigCounts: Map<String, Int>)

    /** Промежуточное состояние: категория и её назначенные сундуки. */
    private class Kind(
        val sig: String,
        val total: Int,
        val maxStack: Int,
        val perContainer: Map<BlockPos, Int>,
        val targets: MutableList<BlockPos>,
        val targetSet: MutableSet<BlockPos>,
        val otherHolders: List<BlockPos>,
        val entitled: Boolean,
        val anchor: BlockPos
    ) {
        var capacity = 0
        var movable = 0
        var holdersInTargets = 0

        /** Сколько ещё слотов не хватает этой категории. */
        fun deficitSlots(): Int {
            val items = movable - capacity
            return if (items <= 0) 0 else (items + maxStack - 1) / maxStack
        }
    }

    /** Готовый план: задания, назначения и предупреждения. */
    data class Plan(val jobs: List<SweepJob>, val sigTargets: Map<String, List<BlockPos>>, val claimedEmpties: Set<BlockPos>, val warnings: List<String>)

    /** «В этом сундуке забери вот эти категории». */
    data class SweepJob(val pos: BlockPos, val withdrawSigs: Set<String>?)
}
