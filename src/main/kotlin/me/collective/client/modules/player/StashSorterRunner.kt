package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.utils.sort.ChestNavigator
import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.sort.SortGroupKey
import net.minecraft.core.BlockPos
import net.minecraft.world.inventory.ClickType

/**
 * State-handler'ы рабочего цикла [StashSorter] (SELECT/NAV/OPEN/VERIFY/
 * WITHDRAW/CLOSE/DEPOSIT/DONE). Помощники (skip/note/pause) остаются в модуле
 * package-private: их зовут и из GUI, и из рендера.
 */
object StashSorterRunner {

    @JvmStatic
    fun handleSelectJob(b: StashSorter) {
        if (b.cargoScan().isNotEmpty()) {
            b.state = StashSorter.SortState.SELECT_DEST
        } else if (b.jobIdx >= b.jobs.size) {
            b.state = StashSorter.SortState.DONE
        } else {
            b.currentJob = b.jobs[b.jobIdx]
            b.openRetries = 0
            b.state = StashSorter.SortState.NAV_SOURCE
            b.timer = b.moveDelay.value
        }
    }

    @JvmStatic
    fun handleNavSource(b: StashSorter) {
        if (b.currentJob == null) {
            b.state = StashSorter.SortState.SELECT_JOB
            return
        }
        if (b.containerGone(b.currentJob!!.pos)) {
            b.skipJob("сундука больше нет")
            return
        }

        when (b.nav().travelTo(b.currentJob!!.pos)) {
            ChestNavigator.Status.ARRIVED -> {
                b.state = StashSorter.SortState.OPEN_SOURCE
                b.timer = b.moveDelay.value
            }
            ChestNavigator.Status.UNREACHABLE -> b.skipJob(b.nav().failure())
            ChestNavigator.Status.TRAVELLING -> {
                if (b.timedOut(b.navTimeout.value)) b.skipJob("не дошёл за отведённое время")
            }
        }
    }

    @JvmStatic
    fun handleOpenSource(b: StashSorter) {
        if (b.currentJob == null) return
        when (b.stepOpen(b.currentJob!!.pos)) {
            StashSorter.OpenStep.WAITING -> {
                if (b.timedOut(StashSorter.OPEN_ALIGN_TIMEOUT)) reapproachSource(b, "не смог встать и навестись на сундук")
            }
            StashSorter.OpenStep.CLICKED -> {
                b.menuSettled = false
                b.state = StashSorter.SortState.VERIFY_SOURCE
                b.timer = b.openDelay.value
            }
            StashSorter.OpenStep.NO_ANGLE -> reapproachSource(b, "нет прямого клика по сундуку")
        }
    }

    @JvmStatic
    fun reapproachSource(b: StashSorter, reason: String) {
        if (++b.openRetries >= StashSorter.MAX_RETRIES) b.skipJob(reason)
        else b.state = StashSorter.SortState.NAV_SOURCE
    }

    @JvmStatic
    fun handleVerifySource(b: StashSorter) {
        if (b.currentJob == null) return
        val menu = b.openContainerMenu()
        if (menu == null) {
            if (b.wrongMenuOpen()) {
                b.closeOpenContainer()
                b.skipJob("открылось не то")
            } else if (b.timedOut(StashSorter.OPEN_ALIGN_TIMEOUT)) {
                if (++b.openRetries >= StashSorter.MAX_RETRIES) b.skipJob("не открылся за " + StashSorter.MAX_RETRIES + " попытки")
                else {
                    b.closeOpenContainer()
                    b.state = StashSorter.SortState.OPEN_SOURCE
                    b.timer = b.openDelay.value
                }
            }
            return
        }

        // Ждём, пока сервер досласт содержимое
        if (!b.menuSettled) {
            b.menuSettled = true
            b.timer = b.openDelay.value
            return
        }

        b.openRetries = 0
        val slots = b.computeWithdrawSlots(menu)
        if (slots.isEmpty()) {
            b.reindexOpenContainer(b.currentJob!!.pos)
            b.noteIfEmptied(menu, b.currentJob!!.pos)
            b.jobIdx++
            b.chestsSwept++
            b.state = StashSorter.SortState.CLOSE_SOURCE
        } else {
            b.state = StashSorter.SortState.WITHDRAW
        }
    }

    @JvmStatic
    fun handleWithdraw(b: StashSorter) {
        if (b.currentJob == null) return
        if (b.timedOut(b.stuckTimeout.value)) {
            b.skipJob("застрял при выдаче (нет прогресса)")
            return
        }

        val menu = b.openContainerMenu()
        if (menu == null) {
            if (++b.openRetries >= StashSorter.MAX_RETRIES) b.skipJob("сундук закрывается в середине выдачи")
            else {
                b.note("сундук закрылся при выдаче", "открываю заново (" + b.openRetries + "/" + StashSorter.MAX_RETRIES + ")")
                b.state = StashSorter.SortState.OPEN_SOURCE
                b.timer = b.openDelay.value
            }
            return
        }

        val free = b.ferryFreeSlots()
        val slots = b.computeWithdrawSlots(menu)
        if (slots.isEmpty() || free <= 0) {
            if (slots.isEmpty()) {
                b.jobIdx++
                b.chestsSwept++
                b.noteIfEmptied(menu, b.currentJob!!.pos)
            } else if (b.cargoScan().isEmpty()) {
                // Переносить некуда: в инвентаре лежит то, что сортировщик не трогает
                b.enterPause(
                    StashSorter.PauseReason.INVENTORY_FULL,
                    "Нет свободных слотов для переноски — инвентарь занят тем, что сортировщик не двигает"
                )
                return
            }
            b.reindexOpenContainer(b.currentJob!!.pos)
            b.state = StashSorter.SortState.CLOSE_SOURCE
            return
        }

        var clicks = 0
        for (slotId in slots) {
            if (clicks >= b.clicksPerTick.value || clicks >= free) break
            val stack = menu.slots[slotId].item
            if (ContainerGeometry.isShulkerItem(stack)) b.shulkersMoved++
            else b.stacksMoved++
            ClientAccess.collectiveMinecraft.gameMode!!.handleInventoryMouseClick(
                menu.containerId, slotId, 0, ClickType.QUICK_MOVE, ClientAccess.collectiveMinecraft.player!!
            )
            clicks++
        }
    }

    @JvmStatic
    fun handleCloseSource(b: StashSorter) {
        b.closeOpenContainer()
        b.timer = b.openDelay.value
        b.state = if (b.cargoScan().isEmpty()) StashSorter.SortState.SELECT_JOB else StashSorter.SortState.SELECT_DEST
    }

    @JvmStatic
    fun handleSelectDest(b: StashSorter) {
        val cargo = b.cargoScan()
        if (cargo.isEmpty()) {
            b.state = StashSorter.SortState.SELECT_JOB
            b.timer = b.moveDelay.value
            return
        }

        val me = ClientAccess.collectiveMinecraft.player!!.blockPosition()
        var bestKey: String? = null
        var bestDest: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        // Вещём сначала то, до чего ближе всего дойти
        for (key in cargo.keys) {
            val d = b.destFor(key) ?: continue
            val dist = me.distSqr(d)
            if (dist < bestDist) {
                bestDist = dist
                bestDest = d
                bestKey = key
            }
        }

        b.currentRouteKey = bestKey ?: cargo.keys.first()
        b.currentDest = bestDest ?: b.huntEmptyChest(b.currentRouteKey!!)

        // Ничего подходящего нет — пробуем общую «переполненную» категорию
        if (b.currentDest == null) {
            val overflow = SortGroupKey.overflowKey(b.currentRouteKey!!)
            if (overflow != b.currentRouteKey) {
                b.overflowRoutes[b.currentRouteKey!!] = overflow
                b.currentRouteKey = overflow
                b.currentDest = b.destFor(overflow)
                if (b.currentDest == null) b.currentDest = b.huntEmptyChest(overflow)
            }
            if (b.currentDest == null) b.currentDest = b.assignOverflowChest(b.currentRouteKey!!)
        }

        if (b.currentDest == null) {
            b.deadRoutes.add(b.currentRouteKey!!)
            b.note("нет сундука для «" + SortGroupKey.friendlyName(b.currentRouteKey!!) + "»", "вещи останутся в инвентаре")
            b.state = StashSorter.SortState.SELECT_DEST
            return
        }

        b.openRetries = 0
        b.depositLastCount = -1
        b.state = StashSorter.SortState.NAV_DEST
    }

    @JvmStatic
    fun handleNavDest(b: StashSorter) {
        if (b.currentDest == null) {
            b.state = StashSorter.SortState.SELECT_DEST
            return
        }
        if (b.containerGone(b.currentDest!!)) {
            b.skipDest("сундука больше нет")
            return
        }

        when (b.nav().travelTo(b.currentDest)) {
            ChestNavigator.Status.ARRIVED -> {
                b.state = StashSorter.SortState.OPEN_DEST
                b.timer = b.moveDelay.value
            }
            ChestNavigator.Status.UNREACHABLE -> b.skipDest(b.nav().failure())
            ChestNavigator.Status.TRAVELLING -> {
                if (b.timedOut(b.navTimeout.value)) b.skipDest("не дошёл за отведённое время")
            }
        }
    }

    @JvmStatic
    fun handleOpenDest(b: StashSorter) {
        if (b.currentDest == null) return
        when (b.stepOpen(b.currentDest!!)) {
            StashSorter.OpenStep.WAITING -> {
                if (b.timedOut(StashSorter.OPEN_ALIGN_TIMEOUT)) reapproachDest(b, "не смог встать и навестись на сундук")
            }
            StashSorter.OpenStep.CLICKED -> {
                b.menuSettled = false
                b.state = StashSorter.SortState.VERIFY_DEST
                b.timer = b.openDelay.value
            }
            StashSorter.OpenStep.NO_ANGLE -> reapproachDest(b, "нет прямого клика по сундуку")
        }
    }

    @JvmStatic
    fun reapproachDest(b: StashSorter, reason: String) {
        if (++b.openRetries >= StashSorter.MAX_RETRIES) b.skipDest(reason)
        else b.state = StashSorter.SortState.NAV_DEST
    }

    @JvmStatic
    fun handleVerifyDest(b: StashSorter) {
        if (b.currentDest == null || b.currentRouteKey == null) return
        val menu = b.openContainerMenu()
        if (menu == null) {
            if (b.wrongMenuOpen()) {
                b.closeOpenContainer()
                b.markFull(b.currentDest)
                b.note("получатель — не сундук", b.currentDest?.toShortString() ?: "?")
                b.state = StashSorter.SortState.SELECT_DEST
            } else if (b.timedOut(StashSorter.OPEN_ALIGN_TIMEOUT)) {
                if (++b.openRetries >= StashSorter.MAX_RETRIES) {
                    b.markFull(b.currentDest)
                    b.note("получатель не открывается", b.currentDest!!.toShortString())
                    b.state = StashSorter.SortState.SELECT_DEST
                } else {
                    b.closeOpenContainer()
                    b.state = StashSorter.SortState.OPEN_DEST
                    b.timer = b.openDelay.value
                }
            }
            return
        }

        if (!b.menuSettled) {
            b.menuSettled = true
            b.timer = b.openDelay.value
            return
        }

        b.openRetries = 0
        val containerSlots = menu.rowCount * 9
        val empty = b.freeSlotsIn(menu)
        b.knownFree[b.currentDest!!] = empty

        // Сундук брали как пустой под новую категорию, а он оказался занятым
        if (b.emptyClaims.contains(b.currentDest!!)) {
            for (i in 0 until containerSlots) {
                val s = menu.slots[i].item
                if (s.isEmpty || b.matchesRoute(SortGroupKey.of(s), b.currentRouteKey!!)) continue

                b.reindexOpenContainer(b.currentDest)
                // sigTargets объявлен read-only Map, но в рантайме всегда LinkedHashMap с ArrayList-значениями
                for (ts in b.sigTargets.values) (ts as MutableList<BlockPos>).remove(b.currentDest!!)
                if (empty == 0) b.markFull(b.currentDest)
                b.note("получатель не пустой", b.currentDest!!.toShortString() + " — записал в индекс")
                b.state = StashSorter.SortState.CLOSE_DEST
                return
            }

            val targets = b.sigTargets[b.currentRouteKey]
            val dest = b.currentDest
            if (targets != null && dest != null && !targets.contains(dest)) (targets as MutableList<BlockPos>).add(dest)
        }

        if (empty == 0) {
            b.markFull(b.currentDest)
            b.reindexOpenContainer(b.currentDest)
            b.state = StashSorter.SortState.CLOSE_DEST
        } else {
            b.depositLastCount = -1
            b.state = StashSorter.SortState.DEPOSIT
        }
    }

    @JvmStatic
    fun handleDeposit(b: StashSorter) {
        if (b.currentDest == null) return
        if (b.timedOut(b.stuckTimeout.value)) {
            b.markFull(b.currentDest)
            b.note("застрял при выдаче", b.currentDest!!.toShortString())
            b.state = StashSorter.SortState.CLOSE_DEST
            return
        }

        val menu = b.openContainerMenu()
        if (menu == null) {
            if (++b.openRetries >= StashSorter.MAX_RETRIES) {
                b.markFull(b.currentDest)
                b.note("получатель закрылся при выдаче", b.currentDest!!.toShortString())
                b.state = StashSorter.SortState.SELECT_DEST
            } else {
                b.note("сундук закрылся при выдаче", "открываю заново (" + b.openRetries + "/" + StashSorter.MAX_RETRIES + ")")
                b.state = StashSorter.SortState.OPEN_DEST
                b.timer = b.openDelay.value
            }
            return
        }

        val slots = b.depositSlots()
        if (slots.isEmpty()) {
            b.reindexOpenContainer(b.currentDest)
            b.state = StashSorter.SortState.CLOSE_DEST
            return
        }

        // Количество не меняется при кликах — сундук не принимает, не долбимся
        var count = 0
        for (i in slots) count += ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i).count
        if (count == b.depositLastCount) {
            b.markFull(b.currentDest)
            b.reindexOpenContainer(b.currentDest)
            b.state = StashSorter.SortState.CLOSE_DEST
            return
        }
        b.depositLastCount = count

        var clicks = 0
        for (invSlot in slots) {
            if (clicks >= b.clicksPerTick.value) break
            ClientAccess.collectiveMinecraft.gameMode!!.handleInventoryMouseClick(
                menu.containerId, b.invIndexToMenuId(menu, invSlot), 0,
                ClickType.QUICK_MOVE, ClientAccess.collectiveMinecraft.player!!
            )
            clicks++
        }
    }

    @JvmStatic
    fun handleCloseDest(b: StashSorter) {
        b.closeOpenContainer()
        b.timer = b.openDelay.value
        b.state = StashSorter.SortState.SELECT_DEST
    }

    @JvmStatic
    fun handleDone(b: StashSorter) {
        val moved = b.stacksMoved + b.shulkersMoved
        // Один проход не разбирает всё: то, что переехало или просканировалось, меняет раскладку
        if (b.pass < StashSorter.MAX_PASSES && (moved > b.movedAtPassStart || b.chestsSwept > b.sweptAtPassStart)) {
            b.movedAtPassStart = moved
            b.sweptAtPassStart = b.chestsSwept
            b.pass++
            b.printInfo("Проход " + b.pass + ": пересчитываю то, что сортировщик переписал в индексе.")
            b.state = StashSorter.SortState.PLAN
            return
        }

        b.flushNotes()

        val seconds = maxOf(1L, (System.currentTimeMillis() - b.startTime) / 1000L)
        b.printInfo(
            String.format(
                "Готово: сундуков %d, переложено стопок %d и шалкеров %d за %d:%02d%s.",
                b.chestsSwept, b.stacksMoved, b.shulkersMoved, seconds / 60L, seconds % 60L,
                if (b.pass > 1) " (" + b.pass + " проходов)" else ""
            )
        )

        for (i in b.cargoSlots()) {
            val s = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i)
            if (s.isEmpty) continue
            val key = b.routeKeyOf(s, true)
            if (key != null && b.deadRoutes.contains(key)) {
                b.printWarning("Часть вещей не нашла дом и осталась в инвентаре.")
                break
            }
        }

        b.setToggled(false)
    }
}
