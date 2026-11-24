package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.sort.SortGroupKey
import net.minecraft.core.BlockPos
import net.minecraft.world.inventory.ChestMenu
import java.util.TreeMap
import java.util.ArrayList

/**
 * Грузовая логика [StashSorter]: что взять из домашнего сундука, что
 * несёт игрок, куда класть (включая «аварийные дома» для остатков) и учёт
 * пустых/полных сундуков. Читает и ведет карты маршрутов сортировщика.
 *
 * Выделено из тела [StashSorter] по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика живёт здесь.
 */
object StashSorterCargo {

    @JvmStatic
    fun computeWithdrawSlots(b: StashSorter, menu: ChestMenu): List<Int> {
        val containerSlots = menu.rowCount * 9
        val out = ArrayList<Int>()

        for (i in 0 until containerSlots) {
            val s = menu.slots[i].item
            if (s.isEmpty) continue

            val key = StashSorterRouting.routeKeyOf(b, s, false)
            if (key == null || b.deadRoutes.contains(key)) continue

            val targets = b.sigTargets[key] ?: continue
            // Из домашнего сундука ничего не берём — он и так свой
            val jobPos = b.currentJob?.pos
            if (jobPos != null && targets.contains(jobPos)) continue
            if (destFor(b, key) == null && b.currentJob != null &&
         b.currentJob!!.pos == b.overflowChests[SortGroupKey.overflowKey(key)]
            ) continue

            out.add(i)
        }

        return out
    }

    /** Что игрок сейчас несёт, сгруппированное по маршрутам. */
    @JvmStatic
    fun cargoScan(b: StashSorter): Map<String, List<Int>> {
        val out = TreeMap<String, MutableList<Int>>()
        val player = ClientAccess.collectiveMinecraft.player ?: return out

        for (i in cargoSlots(b)) {
            val s = player.inventory.getItem(i)
            if (s.isEmpty) continue
            val key = StashSorterRouting.routeKeyOf(b, s, true)
            if (key != null && !b.deadRoutes.contains(key)) out.getOrPut(key) { ArrayList() }.add(i)
        }
        return out
    }

    /** Слоты инвентаря, которые можно использовать как переноску. */
    @JvmStatic
    fun cargoSlots(b: StashSorter): List<Int> {
        val out = ArrayList<Int>(36)
        for (i in b.reservedHotbarSlots.value until 36) {
            if (!b.keepSlots.contains(i)) out.add(i)
        }
        return out
    }

    @JvmStatic
    fun ferryFreeSlots(b: StashSorter): Int {
        var free = 0
        for (i in cargoSlots(b)) if (ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i).isEmpty) free++
        return free
    }

    @JvmStatic
    fun depositSlots(b: StashSorter): List<Int> {
        val out = ArrayList<Int>()
        val dest = b.currentDest
        for (e in cargoScan(b)) {
            val targets = b.sigTargets[e.key]
            if (e.key == b.currentRouteKey || (targets != null && dest != null && targets.contains(dest))) {
                out.addAll(e.value)
            }
        }
        return out
    }

    @JvmStatic
    fun freeSlotsIn(menu: ChestMenu): Int {
        var free = 0
        val containerSlots = menu.rowCount * 9
        for (i in 0 until containerSlots) if (menu.slots[i].item.isEmpty) free++
        return free
    }

    @JvmStatic
    fun noteIfEmptied(b: StashSorter, menu: ChestMenu, pos: BlockPos) {
        val containerSlots = menu.rowCount * 9
        for (i in 0 until containerSlots) {
            if (!menu.slots[i].item.isEmpty) return
        }

        b.knownFree[pos] = containerSlots
        if (b.markedFull.contains(pos) || b.emptyClaims.contains(pos) || b.spareEmpties.contains(pos)) return
        for (targets in b.sigTargets.values) if (targets.contains(pos)) return

        b.spareEmpties.add(pos)
    }

    @JvmStatic
    fun markFull(b: StashSorter, pos: BlockPos?) {
        val level = ClientAccess.collectiveMinecraft.level!!
        if (pos == null || level == null) return
        b.markedFull.add(pos)
        val other = ContainerGeometry.otherHalf(level, pos)
        if (other != null) b.markedFull.add(other)
    }

    @JvmStatic
    fun claimEmpty(b: StashSorter, pos: BlockPos?) {
        val level = ClientAccess.collectiveMinecraft.level!!
        if (pos == null || level == null) return
        b.emptyClaims.add(pos)
        val other = ContainerGeometry.otherHalf(level, pos)
        if (other != null) b.emptyClaims.add(other)
    }

    @JvmStatic
    fun destFor(b: StashSorter, routeKey: String): BlockPos? {
        val targets = b.sigTargets[routeKey] ?: return null
        for (t in targets) if (!b.markedFull.contains(t)) return t
        return null
    }

    @JvmStatic
    fun huntEmptyChest(b: StashSorter, routeKey: String): BlockPos? {
        for (p in b.unknownContainers()) {
            if (!b.markedFull.contains(p) && !b.emptyClaims.contains(p)) {
                claimEmpty(b, p)
                return p
            }
        }
        for (p in b.spareEmpties) {
            if (!b.markedFull.contains(p) && !b.emptyClaims.contains(p)) {
                claimEmpty(b, p)
                return p
            }
        }
        return null
    }

    /**
     * Аварийный дом: под остаток, которому не нашлось места, берём любой
     * свободный сундук, который ещё не закреплён за другой категорией.
     */
    @JvmStatic
    fun assignOverflowChest(b: StashSorter, overflowKey: String): BlockPos? {
        val existing = b.overflowChests[overflowKey]
        if (existing != null && !b.markedFull.contains(existing)) return existing

        val owned = HashSet<BlockPos>()
        for (ts in b.sigTargets.values) owned.addAll(ts)

        val queued = HashSet<BlockPos>()
        for (i in b.jobIdx until b.jobs.size) queued.add(b.jobs[i].pos)

        val pinned = HashSet<BlockPos>()
        for (pin in b.store().pinsFor(b.dimension())) {
            pinned.add(ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, pin.pos()))
        }

        var best = pickOverflowChest(b, owned, queued, pinned, true)
        if (best == null) best = pickOverflowChest(b, owned, queued, pinned, false)
        if (best == null) return null

        b.overflowChests[overflowKey] = best
        // sigTargets объявлен read-only Map, но в рантайме всегда LinkedHashMap с ArrayList-значениями
        @Suppress("UNCHECKED_CAST")
        val sigTargets = b.sigTargets as MutableMap<String, MutableList<BlockPos>>
        sigTargets.getOrPut(overflowKey) { ArrayList() }.add(best)
        b.note("аварийный дом для «" + SortGroupKey.friendlyName(overflowKey) + "»", best.toShortString())
        return best
    }

    @JvmStatic
    fun pickOverflowChest(
        b: StashSorter,
        owned: Set<BlockPos>,
        queued: Set<BlockPos>,
        pinned: Set<BlockPos>,
        skipOwned: Boolean
    ): BlockPos? {
        val me = ClientAccess.collectiveMinecraft.player!!.blockPosition()
        var best: BlockPos? = null
        var bestFree = 0
        var bestDist = Double.MAX_VALUE

        for (e in b.knownFree) {
            val pos = e.key
            val free = e.value
            if (free <= 0 || b.markedFull.contains(pos) || pinned.contains(pos) ||
         queued.contains(pos) || b.emptyClaims.contains(pos)
            ) continue
            if (skipOwned && owned.contains(pos)) continue

            val dist = me.distSqr(pos)
            // Сначала самый пустой, при равенстве — ближайший
            if (free > bestFree || (free == bestFree && (dist < bestDist || (dist == bestDist && best != null && pos < best)))) {
                best = pos
                bestFree = free
                bestDist = dist
            }
        }
        return best
    }
}
