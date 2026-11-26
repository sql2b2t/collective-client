package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.sort.SortGroupKey
import me.collective.client.utils.sort.SortPlanner
import me.collective.client.utils.tracker.ContainerIndexManager
import net.minecraft.core.BlockPos

/**
 * Планировочная часть [StashSorter]: сборка снимков сундуков зоны для
 * [SortPlanner] — фильтрация по типу и зоне, свежайший дубль по канони-
 * ческому поcу, фильтр сигнатур по настройкам, свёртка мелких категорий.
 *
 * Выделено из тела [StashSorter] по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика живёт здесь.
 */
object StashSorterPlanning {

    @JvmStatic
    fun buildPinMap(b: StashSorter, dim: String): Map<String, List<BlockPos>> {
        val out = LinkedHashMap<String, MutableList<BlockPos>>()
        val seen = HashSet<BlockPos>()
        for (p in b.store().pinsFor(dim)) {
            val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, p.pos())
            if (seen.add(canonical)) out.getOrPut(p.groupKey) { ArrayList() }.add(canonical)
        }
        return out
    }

    @JvmStatic
    fun buildSnapshots(b: StashSorter, dim: String, pinnedKeys: Set<String>): List<SortPlanner.ContainerSnapshot> {
        val data = ContainerIndexManager.getData()
        val areas = b.sourceZones()
        if (areas.isEmpty()) return listOf()

        val byCanonical = HashMap<BlockPos, SortPlanner.ContainerSnapshot>()
        val newest = HashMap<BlockPos, Long>()

        for (tc in data.getAllContainers(dim)) {
            val type = tc.containerType
            if (type != "chest" && type != "copper_chest" && type != "barrel") continue

            val raw = tc.position
            val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, raw)
            if (!b.inArea(areas, raw, canonical)) continue

            val prev = newest[canonical]
            if (prev != null && prev >= tc.lastUpdated) continue
            newest[canonical] = tc.lastUpdated

            val sigs = HashMap<String, Int>()
            for (e in tc.getTopLevelSignatureCounts()) {
                val shulker = SortGroupKey.isShulkerGroup(e.key)
                if (shulker && !b.moveShulkers.value) continue
                if (!shulker && b.looseItems.value == StashSorter.LooseItems.IGNORE) continue
                sigs.merge(e.key, e.value) { a, v -> a + v }
            }

            var used = tc.usedStacks
            if (used == 0 && tc.getTopLevelSignatureCounts().isNotEmpty()) used = 1

            byCanonical[canonical] = SortPlanner.ContainerSnapshot(
                canonical, type, slotCountOf(canonical, type, used), used, sigs
            )
        }

        // Сводим мелкие категории в крупные — планировщик считает уже свёрнутые
        val totals = HashMap<String, Int>()
        for (c in byCanonical.values) {
            for (e in c.sigCounts) {
                totals.merge(e.key, e.value) { a, v -> a + v }
            }
        }

        val collapse = HashMap<String, String>()
        for (key in totals.keys) collapse[key] = StashSorterRouting.collapseKey(b, key, totals, pinnedKeys)
        b.keyCollapse = collapse

        val out = ArrayList<SortPlanner.ContainerSnapshot>(byCanonical.size)
        for (c in byCanonical.values) {
            val sigs = HashMap<String, Int>()
            for (e in c.sigCounts) {
                val group = collapse[e.key]!!
                sigs.merge(group, StashSorterRouting.groupAmount(e.key, group, e.value)) { a, v -> a + v }
            }
            out.add(SortPlanner.ContainerSnapshot(c.pos, c.type, c.estSlots, c.usedSlots, sigs))
        }

        return out
    }

    @JvmStatic
    fun withProbeSnapshots(
        b: StashSorter,
        indexed: List<SortPlanner.ContainerSnapshot>,
        unknown: List<BlockPos>
    ): List<SortPlanner.ContainerSnapshot> {
        if (unknown.isEmpty()) return indexed

        val out = ArrayList<SortPlanner.ContainerSnapshot>(indexed.size + unknown.size)
        out.addAll(indexed)
        for (p in unknown) {
            val type = b.containerTypeAt(p)
            out.add(SortPlanner.ContainerSnapshot(p, type, slotCountOf(p, type, 0), 0, mapOf()))
        }
        return out
    }

    @JvmStatic
    fun slotCountOf(canonical: BlockPos, type: String, used: Int): Int {
        return ContainerGeometry.slotCount(ClientAccess.collectiveMinecraft.level!!, canonical, type, used)
    }

    /**
     * Фаза PLAN: очистка маршрутных карт, захват занятых слотов, сборка
     * снимков и плана консолидации, резервирование «сундуков без записи».
     * Единственный писатель полей jobs/sigTargets/knownFree на этом этапе.
     */
    @JvmStatic
    fun handlePlan(b: StashSorter) {
        val dim = b.dimension()
        b.markedFull.clear()
        b.deadRoutes.clear()
        b.overflowChests.clear()
        b.overflowRoutes.clear()

        // Слоты, которые игрок занял до сортировки, не трогаем
        if (!b.keepSlotsCaptured) {
            b.keepSlotsCaptured = true
            if (!b.depositInventoryFirst.value) {
                for (i in b.reservedHotbarSlots.value until 36) {
                    if (!ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i).isEmpty) b.keepSlots.add(i)
                }
            }
        }

        val pins = buildPinMap(b, dim)
        val snapshots = buildSnapshots(b, dim, pins.keys)
        val origin = ClientAccess.collectiveMinecraft.player!!.blockPosition()
        val unknown = b.unknownContainers()

        if (snapshots.isEmpty()) {
            if (unknown.isEmpty()) {
                b.printWarning("В зоне нет сундуков (ни в индексе, ни в чанках). Открой сундуки зоны один раз, чтобы заполнить индекс.")
                b.setToggled(false)
                return
            }
            b.printInfo("В индексе сундуков нет, но найдено " + unknown.size + " сундуков в зоне. Провожу сканирование...")
            b.jobs = ArrayList()
            for (p in unknown) {
                b.jobs.add(SortPlanner.SweepJob(p, null))
            }
            b.jobIdx = 0
            b.sigTargets = LinkedHashMap()
            b.emptyClaims.clear()
            b.spareEmpties.clear()
            b.knownFree.clear()
            for (p in unknown) b.knownFree[p] = slotCountOf(p, b.containerTypeAt(p), 0)
            b.state = StashSorter.SortState.SELECT_JOB
            return
        }

        b.knownFree.clear()
        for (c in snapshots) {
            b.knownFree[c.pos] = maxOf(0, c.estSlots - c.usedSlots)
        }
        for (p in unknown) b.knownFree[p] = slotCountOf(p, b.containerTypeAt(p), 0)

        val plan = SortPlanner.planConsolidation(
            withProbeSnapshots(b, snapshots, unknown), 1, origin, StashSorterRouting::maxStackOfSig, pins,
            b.minItemsForOwnChest.value, unknown
        )

        for (w in plan.warnings) {
            if (b.verbosity.value == StashSorter.Verbosity.DETAILED) b.printWarning(w)
            else b.note("предупреждение плана", w)
        }

        b.jobs = ArrayList(plan.jobs)
        if (b.verifyEveryChest.value && b.pass == 1) {
            // Первый проход — осмотр всего: так расхождения индекса всплывают сразу
            b.jobs = ArrayList()
            val ordered = ArrayList(snapshots)
            ordered.sortWith(compareBy<SortPlanner.ContainerSnapshot>({ origin.distSqr(it.pos) }, { it.pos }))
            for (c in ordered) {
                if (c.usedSlots > 0) b.jobs.add(SortPlanner.SweepJob(c.pos, null))
            }
        }
        // Если есть сундуки без записи, добавляем их для обязательного осмотра и индексации
        for (p in unknown) {
            var already = false
            for (j in b.jobs) {
                if (j.pos == p) {
                    already = true
                    break
                }
            }
            if (!already) b.jobs.add(SortPlanner.SweepJob(p, null))
        }

        b.jobIdx = 0

        b.sigTargets = LinkedHashMap()
        // sigTargets объявлен read-only Map, но только что заменён на LinkedHashMap — мутируем через mutable-вид
        @Suppress("UNCHECKED_CAST")
        val sigTargets = b.sigTargets as MutableMap<String, MutableList<BlockPos>>
        for (e in plan.sigTargets) {
            sigTargets[e.key] = ArrayList(e.value)
        }

        b.emptyClaims.clear()
        b.emptyClaims.addAll(plan.claimedEmpties)

        val pinnedPos = HashSet<BlockPos>()
        for (ps in pins.values) pinnedPos.addAll(ps)

        // spareEmpties пересобирается на каждом проходе: без очистки
        // список копит дубликаты из прошлых проходов.
        b.spareEmpties.clear()
        var emptyIndexed = 0
        for (c in snapshots) {
            if (c.usedSlots == 0 && !pinnedPos.contains(c.pos)) {
                emptyIndexed++
                if (!plan.claimedEmpties.contains(c.pos)) b.spareEmpties.add(c.pos)
            }
        }
        b.spareEmpties.sortWith(compareBy<BlockPos>({ origin.distSqr(it) }, { it }))

        b.printInfo(
            "План готов" + (if (b.pass > 1) " (проход " + b.pass + ")" else "") + ": сундуков в зоне " +
         snapshots.size + " (пустых " + emptyIndexed +
         (if (unknown.isEmpty()) "" else ", без записи " + unknown.size) + "), к разбору " + b.jobs.size +
         (if (plan.claimedEmpties.isEmpty()) "" else ", занято новых сундуков " + plan.claimedEmpties.size) + "."
        )

        b.state = if (b.depositInventoryFirst.value) StashSorter.SortState.SELECT_DEST else StashSorter.SortState.SELECT_JOB
    }
}
