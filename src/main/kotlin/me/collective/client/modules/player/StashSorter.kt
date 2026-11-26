package me.collective.client.modules.player

import java.util.ArrayList
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.sort.ChestNavigator
import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.sort.SortGroupKey
import me.collective.client.utils.sort.SortPlanner
import me.collective.client.utils.sort.SortZone
import me.collective.client.utils.sort.SorterWorldStore
import me.collective.client.utils.tracker.ContainerIndex
import me.collective.client.utils.tracker.ContainerIndexManager
import me.collective.client.utils.tracker.TrackedContainer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.awt.Color

/**
 * Автосортировка стеша (порт из BepHax, там — StashSorter).
 *
 * <p>Как работает: сортировщик не перекладывает вещи из сундука в сундук
 * напрямую (так нельзя — открыт только один контейнер), а возит их в
 * инвентаре: забрал нужное из источника → положил в получателя. Планировщик
 * заранее решает, где должен лежать каждый тип вещей, а машина состояний
 * обходит сундуки и перекладывает.
 *
 * <p>Данные берутся из индекса контейнеров ({@link ContainerIndex}), поэтому
 * перед сортировкой нужно один раз открыть сундуки стеша. Зона задаётся
 * командой {@code .сорт зона}, метки — {@code .сорт метка}.
 */
class StashSorter : Module("StashSorter", "Автоматическая сортировка сундуков стеша", ModuleCategory.PLAYER, "stashsorter") {
   @JvmField
   val moveShulkers: Setting<Boolean>
   @JvmField
   val looseItems: Setting<LooseItems>
   @JvmField
   val minShulkersForOwnChest: Setting<Int>
   @JvmField
   val minItemsForOwnChest: Setting<Int>
   @JvmField
   val reservedHotbarSlots: Setting<Int>
   @JvmField
   val depositInventoryFirst: Setting<Boolean>
   @JvmField
   val verifyEveryChest: Setting<Boolean>
   private val safePathing: Setting<Boolean>
   @JvmField
   val navTimeout: Setting<Int>
   @JvmField
   val stuckTimeout: Setting<Int>
   @JvmField
   val settleDelay: Setting<Int>
   @JvmField
   val moveDelay: Setting<Int>
   @JvmField
   val openDelay: Setting<Int>
   @JvmField
   val clicksPerTick: Setting<Int>
   @JvmField
   val turnSpeed: Setting<Int>
   @JvmField
   val verbosity: Setting<Verbosity>
   private val showZones: Setting<Boolean>
   private val zoneColor: Setting<Color>
   private val showLabels: Setting<Boolean>
   private val labelColor: Setting<Color>
   private val pinnedColor: Setting<Color>
   @JvmField
   val overlayRange: Setting<Int>
   private val pauseOnStrangers: Setting<Boolean>
   private val pauseOnLag: Setting<Boolean>

   // --- состояние прогона ---
   private var lastPacketTime = System.currentTimeMillis()

   @JvmField
   var state = SortState.IDLE
   private var lastState = SortState.IDLE
   private var stateTicks = 0
   @JvmField
   var timer = 0
   @JvmField
   var openRetries = 0
   @JvmField
   var menuSettled = false
   @JvmField
   var settleTicks = 0
   @JvmField
   var alignTicks = 0

   @JvmField
   var startTime: Long = 0
   @JvmField
   var pass = 1
   @JvmField
   var movedAtPassStart = 0
   @JvmField
   var sweptAtPassStart = 0
   @JvmField
   var chestsSwept = 0
   @JvmField
   var stacksMoved = 0
   @JvmField
   var shulkersMoved = 0
   @JvmField
   var jobIdx = 0
   @JvmField
   var depositLastCount = -1
   @JvmField
   var keepSlotsCaptured = false
   private var pauseReason = PauseReason.MANUAL

   @JvmField
   var jobs: MutableList<SortPlanner.SweepJob> = ArrayList()
   @JvmField
   var currentJob: SortPlanner.SweepJob? = null
   @JvmField
   var currentDest: BlockPos? = null
   @JvmField
   var currentRouteKey: String? = null

   @JvmField
   var sigTargets: Map<String, List<BlockPos>> = emptyMap()
   @JvmField
   var keyCollapse: Map<String, String> = emptyMap()
   @JvmField
   val markedFull: MutableSet<BlockPos> = HashSet()
   @JvmField
   val keepSlots: MutableSet<Int> = HashSet()
   @JvmField
   val deadRoutes: MutableSet<String> = HashSet()
   @JvmField
   val overflowChests: MutableMap<String, BlockPos> = HashMap()
   @JvmField
   val overflowRoutes: MutableMap<String, String> = HashMap()
   @JvmField
   val knownFree: MutableMap<BlockPos, Int> = HashMap()
   @JvmField
   val emptyClaims: MutableSet<BlockPos> = HashSet()
   @JvmField
   val spareEmpties: MutableList<BlockPos> = ArrayList()

   /** Причина пропуска -> сколько раз встретилась. Печатается сводкой в конце. */
   private val pendingNotes: MutableMap<String, Int> = LinkedHashMap()
   private val pendingNoteDetail: MutableMap<String, String> = LinkedHashMap()

   private var unknownCache: List<BlockPos> = emptyList()
   private var unknownCacheTime: Long = 0
   private var unknownCacheDim = ""

   private var nav: ChestNavigator? = null
   private var worldStore: SorterWorldStore? = null
   private var selectionCorner1: BlockPos? = null

   private val overlayCache: MutableList<OverlayEntry> = ArrayList()
   private var overlayCacheTime: Long = 0
   private var overlayCacheDim = ""

   init {
      this.moveShulkers = this.add(BooleanSetting("MoveShulkers", true))
      this.looseItems = this.add(EnumSetting<LooseItems>("LooseItems", LooseItems.MIXED_CHEST))
      this.minShulkersForOwnChest = this.add(NumberSetting<Int>("MinShulkersOwnChest", 4, 1, 64))
      this.minItemsForOwnChest = this.add(NumberSetting<Int>("MinItemsOwnChest", 32, 1, 2048))
      this.reservedHotbarSlots = this.add(NumberSetting<Int>("ReservedHotbar", 1, 0, 8))
      this.depositInventoryFirst = this.add(BooleanSetting("DepositFirst", true))
      this.verifyEveryChest = this.add(BooleanSetting("VerifyEveryChest", false))
      this.safePathing = this.add(BooleanSetting("SafePathing", true))
      this.navTimeout = this.add(NumberSetting<Int>("NavTimeout", 200, 20, 2000))
      this.stuckTimeout = this.add(NumberSetting<Int>("StuckTimeout", 60, 10, 600))
      this.settleDelay = this.add(NumberSetting<Int>("SettleDelay", 2, 0, 20))
      this.moveDelay = this.add(NumberSetting<Int>("MoveDelay", 2, 0, 40))
      this.openDelay = this.add(NumberSetting<Int>("OpenDelay", 4, 0, 40))
      this.clicksPerTick = this.add(NumberSetting<Int>("ClicksPerTick", 6, 1, 27))
      this.turnSpeed = this.add(NumberSetting<Int>("TurnSpeed", 45, 0, 180))
      this.verbosity = this.add(EnumSetting<Verbosity>("Verbosity", Verbosity.SUMMARY))
      this.showZones = this.add(BooleanSetting("Zones", true).markVisibilityDependency())
      this.zoneColor = this.add(ColorSetting("ZoneColor", Color(0, 220, 255, 220)).dependsOn(this.showZones))
      this.showLabels = this.add(BooleanSetting("Labels", true).markVisibilityDependency())
      this.labelColor = this.add(ColorSetting("LabelColor", Color(255, 255, 255, 230)).dependsOn(this.showLabels))
      this.pinnedColor = this.add(ColorSetting("PinnedColor", Color(255, 200, 60, 230)).dependsOn(this.showLabels))
      this.overlayRange = this.add(NumberSetting<Int>("OverlayRange", 24, 4, 64).withUnit("m").dependsOn(this.showLabels))
      this.pauseOnStrangers = this.add(BooleanSetting("PauseOnStrangers", false))
      this.pauseOnLag = this.add(BooleanSetting("PauseOnLag", true))
   }

   enum class Verbosity(private val displayName: String) : NamedEntry {
      SUMMARY("Только итог"),
      BRIEF("Кратко"),
      DETAILED("Подробно");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class LooseItems(private val displayName: String) : NamedEntry {
      MIXED_CHEST("Смешанный сундук"),
      OWN_CHESTS("Свои сундуки"),
      IGNORE("Не трогать");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class OpenStep {
      WAITING,
      CLICKED,
      NO_ANGLE
   }

   enum class PauseReason {
      STRANGER,
      MANUAL,
      INVENTORY_FULL
   }

   enum class SortState {
      IDLE,
      PLAN,
      SELECT_JOB,
      NAV_SOURCE,
      OPEN_SOURCE,
      VERIFY_SOURCE,
      WITHDRAW,
      CLOSE_SOURCE,
      SELECT_DEST,
      NAV_DEST,
      OPEN_DEST,
      VERIFY_DEST,
      DEPOSIT,
      CLOSE_DEST,
      DONE,
      PAUSED
   }

   private data class OverlayEntry(val pos: BlockPos, val pinned: Boolean)

   // ------------------------------------------------------------------
   //  Жизненный цикл
   // ------------------------------------------------------------------

   override fun onEnable() {
      this.worldStore = SorterWorldStore()
      this.worldStore!!.load()
      ContainerIndexManager.onModuleActivate()
      this.resetRun()
      this.state = SortState.IDLE
      this.lastState = SortState.IDLE

      val zones = this.sourceZones()
      if (zones.isEmpty()) {
         this.printInfo("Зона сортировки не задана. Посмотри на первый угол и выполни: §7.сорт зона§f, потом на второй — ещё раз.")
      } else {
         this.printInfo("Зон: §f" + zones.size + "§f, сундуков в индексе: §f" +
         ContainerIndexManager.getData().getAllContainers(this.dimension()).size)
         this.startSorting()
      }
   }

   override fun onDisable() {
      ContainerIndexManager.saveData()
      ContainerIndexManager.onModuleDeactivate()
      if (this.nav != null) {
         this.nav!!.restorePathing()
         this.nav!!.stop()
      }
      this.state = SortState.IDLE
      this.overlayCache.clear()
   }

   private fun resetRun() {
      this.markedFull.clear()
      this.deadRoutes.clear()
      this.overflowChests.clear()
      this.overflowRoutes.clear()
      this.emptyClaims.clear()
      this.spareEmpties.clear()
      this.keepSlots.clear()
      this.keepSlotsCaptured = false
      this.jobs = ArrayList()
      this.sigTargets = emptyMap()
      this.keyCollapse = emptyMap()
      this.currentJob = null
      this.currentDest = null
      this.currentRouteKey = null
      this.jobIdx = 0
      this.pass = 1
      this.movedAtPassStart = 0
      this.sweptAtPassStart = 0
      this.chestsSwept = 0
      this.stacksMoved = 0
      this.shulkersMoved = 0
      this.openRetries = 0
      this.depositLastCount = -1
      this.menuSettled = false
      this.timer = 0
      this.pendingNotes.clear()
      this.pendingNoteDetail.clear()
      this.startTime = System.currentTimeMillis()
   }

   // ------------------------------------------------------------------
   //  Тик: один шаг машины состояний за раз
   // ------------------------------------------------------------------

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      this.lastPacketTime = System.currentTimeMillis()
   }

   @EventHandler
   private fun onTick(event: LocalPlayerTickEvent) {
      if (this.isWorldUnavailable()) return

      ContainerIndexManager.tickAutosave()
      if (this.nav != null) this.nav!!.setSafePathing(this.safePathing.getValue())

      if (ClientAccess.collectiveMinecraft.player!!.isDeadOrDying()) {
         this.printWarning("Игрок умер — сортировка остановлена.")
         this.setToggled(false)
         return
      }

      // При лагах кликаем не туда: лучше постоять
      if (this.pauseOnLag.getValue() && System.currentTimeMillis() - this.lastPacketTime > 1500L) return

      if (this.state != this.lastState) {
         this.lastState = this.state
         this.stateTicks = 0
         this.settleTicks = 0
         this.alignTicks = 0
      } else {
         this.stateTicks++
      }

      if (this.pauseOnStrangers.getValue() && this.state != SortState.PAUSED && this.state != SortState.IDLE && this.strangerNearby()) {
         this.enterPause(PauseReason.STRANGER, "Рядом чужой игрок")
         return
      }

      if (this.state == SortState.PAUSED) {
         if (this.pauseReason == PauseReason.STRANGER && (!this.pauseOnStrangers.getValue() || !this.strangerNearby())) {
            this.printInfo("Чужой ушёл — продолжаю.")
            this.resumeSorting()
         }
         return
      }

      if (this.state == SortState.IDLE) return

      // Вещи, которые уже разложены, сами собой исчезают из слотов переноски
      this.keepSlots.removeIf { i -> ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i).isEmpty() }

      if (this.timer > 0) {
         this.timer--
         return
      }

      when (this.state) {
         SortState.PLAN -> this.handlePlan()
         SortState.SELECT_JOB -> this.handleSelectJob()
         SortState.NAV_SOURCE -> this.handleNavSource()
         SortState.OPEN_SOURCE -> this.handleOpenSource()
         SortState.VERIFY_SOURCE -> this.handleVerifySource()
         SortState.WITHDRAW -> this.handleWithdraw()
         SortState.CLOSE_SOURCE -> this.handleCloseSource()
         SortState.SELECT_DEST -> this.handleSelectDest()
         SortState.NAV_DEST -> this.handleNavDest()
         SortState.OPEN_DEST -> this.handleOpenDest()
         SortState.VERIFY_DEST -> this.handleVerifyDest()
         SortState.DEPOSIT -> this.handleDeposit()
         SortState.CLOSE_DEST -> this.handleCloseDest()
         SortState.DONE -> this.handleDone()
         else -> {
         }
      }
   }

   // ------------------------------------------------------------------
   //  Шаги
   // ------------------------------------------------------------------

   private fun handlePlan() {
      StashSorterPlanning.handlePlan(this)
   }

   private fun handleSelectJob() {
      StashSorterRunner.handleSelectJob(this)
   }

   private fun handleNavSource() {
      StashSorterRunner.handleNavSource(this)
   }

   private fun handleOpenSource() {
      StashSorterRunner.handleOpenSource(this)
   }

   private fun reapproachSource(reason: String) {
      StashSorterRunner.reapproachSource(this, reason)
   }

   private fun handleVerifySource() {
      StashSorterRunner.handleVerifySource(this)
   }

   private fun handleWithdraw() {
      StashSorterRunner.handleWithdraw(this)
   }

   private fun handleCloseSource() {
      StashSorterRunner.handleCloseSource(this)
   }

   private fun handleSelectDest() {
      StashSorterRunner.handleSelectDest(this)
   }

   private fun handleNavDest() {
      StashSorterRunner.handleNavDest(this)
   }

   private fun handleOpenDest() {
      StashSorterRunner.handleOpenDest(this)
   }

   private fun reapproachDest(reason: String) {
      StashSorterRunner.reapproachDest(this, reason)
   }

   private fun handleVerifyDest() {
      StashSorterRunner.handleVerifyDest(this)
   }

   private fun handleDeposit() {
      StashSorterRunner.handleDeposit(this)
   }

   private fun handleCloseDest() {
      StashSorterRunner.handleCloseDest(this)
   }

   private fun handleDone() {
      StashSorterRunner.handleDone(this)
   }

   fun skipJob(reason: String) {
      this.closeOpenContainer()
      this.nav().stop()
      this.note("пропущен сундук", (if (this.currentJob == null) "?" else this.currentJob!!.pos.toShortString()) + " — " + reason)
      this.jobIdx++
      this.state = SortState.SELECT_JOB
      this.timer = this.moveDelay.getValue()
   }

   fun skipDest(reason: String) {
      this.closeOpenContainer()
      this.nav().stop()
      this.markFull(this.currentDest)
      this.note("проблемный получатель", (if (this.currentDest == null) "?" else this.currentDest!!.toShortString()) + " — " + reason)
      this.state = SortState.SELECT_DEST
   }

   /**
    * Сообщение о походовой неудаче. На большом стеше таких сотни, поэтому в
    * режиме «только итог» текст не пишется — вместо него копится счётчик,
    * и всё накопленное показывается одной строкой в конце прогона.
    */
   fun note(reason: String, detail: String) {
      if (this.verbosity.getValue() == Verbosity.DETAILED) {
         this.printWarning("Пропуск: " + reason + " — " + detail + ".")
         return
      }
      this.pendingNotes.merge(reason, 1, Integer::sum)
      if (this.verbosity.getValue() == Verbosity.BRIEF && !this.pendingNoteDetail.containsKey(reason)) {
         this.pendingNoteDetail[reason] = detail
      }
   }

   /** Накопленная сводка пропусков. Одна строка в конце прогона. */
   fun flushNotes() {
      if (this.pendingNotes.isEmpty()) return

      val sb = StringBuilder("Пропусков: ")
      var first = true
      for (e in this.pendingNotes.entries) {
         if (!first) sb.append(", ")
         first = false
         sb.append(e.key).append(" ×").append(e.value)
      }
      this.printWarning(sb.toString() + ".")

      if (this.verbosity.getValue() == Verbosity.BRIEF) {
         for (e in this.pendingNotes.entries) {
            val detail = this.pendingNoteDetail[e.key]
            if (detail != null) this.printWarning("  " + e.key + ": " + detail)
         }
      }

      this.pendingNotes.clear()
      this.pendingNoteDetail.clear()
   }

   fun enterPause(reason: PauseReason, message: String) {
      this.closeOpenContainer()
      this.nav().stop()
      this.pauseReason = reason
      this.state = SortState.PAUSED
      this.printWarning(message + " — на паузе. Продолжить: §7.сорт пауза§f.")
   }

   // ------------------------------------------------------------------
   //  Что и куда
   // ------------------------------------------------------------------

   fun computeWithdrawSlots(menu: ChestMenu): List<Int> {
      return StashSorterCargo.computeWithdrawSlots(this, menu)
   }

   /** Что игрок сейчас несёт, сгруппированное по маршрутам. */
   fun cargoScan(): Map<String, List<Int>> {
      return StashSorterCargo.cargoScan(this)
   }

   /**
    * Куда едет эта стопка. Мелкие категории сводим в общую, если так настроено,
    * а если дома нет — в «переполненное», иначе вещи останутся в инвентаре.
    */
   fun routeKeyOf(s: ItemStack, allowOverflow: Boolean): String? {
      return StashSorterRouting.routeKeyOf(this, s, allowOverflow)
   }

   fun groupOf(key: String): String {
      return StashSorterRouting.groupOf(this, key)
   }

   fun matchesRoute(key: String, routeKey: String): Boolean {
      return StashSorterRouting.matchesRoute(this, key, routeKey)
   }

   /** Слоты инвентаря, которые можно использовать как переноску. */
   fun cargoSlots(): List<Int> {
      return StashSorterCargo.cargoSlots(this)
   }

   fun ferryFreeSlots(): Int {
      return StashSorterCargo.ferryFreeSlots(this)
   }

   fun depositSlots(): List<Int> {
      return StashSorterCargo.depositSlots(this)
   }

   fun freeSlotsIn(menu: ChestMenu): Int {
      return StashSorterCargo.freeSlotsIn(menu)
   }

   fun noteIfEmptied(menu: ChestMenu, pos: BlockPos) {
      StashSorterCargo.noteIfEmptied(this, menu, pos)
   }

   fun markFull(pos: BlockPos?) {
      StashSorterCargo.markFull(this, pos)
   }

   fun claimEmpty(pos: BlockPos?) {
      StashSorterCargo.claimEmpty(this, pos)
   }

   fun destFor(routeKey: String): BlockPos? {
      return StashSorterCargo.destFor(this, routeKey)
   }

   fun huntEmptyChest(routeKey: String): BlockPos? {
      return StashSorterCargo.huntEmptyChest(this, routeKey)
   }

   /**
    * Аварийный дом: под остаток, которому не нашлось места, берём любой
    * свободный сундук, который ещё не закреплён за другой категорией.
    */
   fun assignOverflowChest(overflowKey: String): BlockPos? {
      return StashSorterCargo.assignOverflowChest(this, overflowKey)
   }

   fun pickOverflowChest(owned: Set<BlockPos>, queued: Set<BlockPos>, pinned: Set<BlockPos>, skipOwned: Boolean): BlockPos? {
      return StashSorterCargo.pickOverflowChest(this, owned, queued, pinned, skipOwned)
   }

   /** Сундуки в зоне, которых нет в индексе: содержимое неизвестно. */
   fun unknownContainers(): List<BlockPos> {
      val now = System.currentTimeMillis()
      val dim = this.dimension()
      if (now - this.unknownCacheTime < 5000L && dim == this.unknownCacheDim) return this.unknownCache

      this.unknownCacheTime = now
      this.unknownCacheDim = dim

      val data = ContainerIndexManager.getData()
      val areas = this.sourceZones()
      if (areas.isEmpty()) {
         this.unknownCache = emptyList()
         return this.unknownCache
      }

      val me = ClientAccess.collectiveMinecraft.player!!.blockPosition()
      val seen: MutableSet<BlockPos> = HashSet()
      val out: MutableList<BlockPos> = ArrayList()

      // Смотрим только загруженные чанки вокруг игрока: полный обход мира
      // на 2b2t — это минуты ожидания на каждый план
      val meChunkX = me.x.toInt() shr 4
      val meChunkZ = me.z.toInt() shr 4
      val chunkRadius = kotlin.math.max(1, this.overlayRange.getValue() / 16)

      for (cx in meChunkX - chunkRadius..meChunkX + chunkRadius) {
         for (cz in meChunkZ - chunkRadius..meChunkZ + chunkRadius) {
            if (!ClientAccess.collectiveMinecraft.level!!.chunkSource.hasChunk(cx, cz)) continue
            val chunk = ClientAccess.collectiveMinecraft.level!!.getChunk(cx, cz, ChunkStatus.FULL, false) as LevelChunk?
            if (chunk == null) continue

            for (blockEntity in chunk.blockEntities.values) {
               if (!ContainerGeometry.isContainerBlock(blockEntity.blockState)) continue

               val raw = blockEntity.blockPos
               val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, raw)
               if (!seen.add(canonical) || !this.inArea(areas, raw, canonical)) continue

               if (data.getContainer(canonical, dim) != null) continue
               val other = ContainerGeometry.otherHalf(ClientAccess.collectiveMinecraft.level!!, canonical)
               if (other != null && data.getContainer(other, dim) != null) continue

               out.add(canonical.immutable())
            }
         }
      }

      out.sortWith(Comparator.comparingDouble<BlockPos> { p: BlockPos -> me.distSqr(p) }.thenComparing { p: BlockPos -> p })
      this.unknownCache = out
      return this.unknownCache
   }

   fun maxStackOfSig(sig: String): Int {
      return StashSorterRouting.maxStackOfSig(sig)
   }

   fun inArea(areas: List<SortZone>, raw: BlockPos, canonical: BlockPos): Boolean {
      if (containsAny(areas, raw) || containsAny(areas, canonical)) return true
      val other = ContainerGeometry.otherHalf(ClientAccess.collectiveMinecraft.level!!, canonical)
      return other != null && containsAny(areas, other)
   }

   fun isInSortArea(canonical: BlockPos): Boolean {
      val areas = this.sourceZones()
      return !areas.isEmpty() && this.inArea(areas, canonical, canonical)
   }

   private fun containsAny(areas: List<SortZone>, pos: BlockPos): Boolean {
      for (z in areas) if (z.contains(pos)) return true
      return false
   }

   // ------------------------------------------------------------------
   //  Снимки индекса для планировщика
   // ------------------------------------------------------------------

   private fun buildPinMap(dim: String): Map<String, List<BlockPos>> {
      return StashSorterPlanning.buildPinMap(this, dim)
   }

   private fun buildSnapshots(dim: String, pinnedKeys: Set<String>): List<SortPlanner.ContainerSnapshot> {
      return StashSorterPlanning.buildSnapshots(this, dim, pinnedKeys)
   }

   private fun slotCountOf(canonical: BlockPos, type: String, used: Int): Int {
      return StashSorterPlanning.slotCountOf(canonical, type, used)
   }

   // ------------------------------------------------------------------
   //  Открытие сундука
   // ------------------------------------------------------------------

   fun stepOpen(canonicalPos: BlockPos): OpenStep {
      return StashSorterAccess.stepOpen(this, canonicalPos)
   }

   fun openContainerMenu(): ChestMenu? {
      return StashSorterAccess.openContainerMenu(this)
   }

   fun wrongMenuOpen(): Boolean {
      return StashSorterAccess.wrongMenuOpen(this)
   }

   fun containerGone(pos: BlockPos): Boolean {
      return StashSorterAccess.containerGone(this, pos)
   }

   /** Индекс слота инвентаря в номере слотов экрана: у контейнера сверху, у игрока снизу. */
   fun invIndexToMenuId(menu: ChestMenu, invIndex: Int): Int {
      return StashSorterAccess.invIndexToMenuId(menu, invIndex)
   }

   /**
    * Закрыть контейнер. В 1.21.11 {@code closeHandledScreen()} у игрока
    * protected, поэтому закрываем экран — он сам отправит пакет закрытия.
    */
   fun closeOpenContainer() {
      StashSorterAccess.closeOpenContainer(this)
   }

   /** Перезаписывает запись индекса тем, что прямо сейчас в сундуке. */
   fun reindexOpenContainer(pos: BlockPos?) {
      if (pos == null || this.isWorldUnavailable()) return

      val data = ContainerIndexManager.getData()
      val h: AbstractContainerMenu? = ClientAccess.collectiveMinecraft.player!!.containerMenu
      if (h == null || h.slots.size < 37) return

      val cont = h.slots.size - 36
      val contents = ArrayList<ItemStack>()
      for (i in 0 until cont) {
         val s = h.slots[i].item
         if (!s.isEmpty) contents.add(s.copy())
      }

      this.knownFree[ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, pos)] = cont - contents.size

      val dim = this.dimension()
      var key: BlockPos = pos
      if (data.getContainer(pos, dim) == null) {
         val other = ContainerGeometry.otherHalf(ClientAccess.collectiveMinecraft.level!!, pos)
         if (other != null && data.getContainer(other, dim) != null) key = other
      }
      data.trackContainer(key, dim, this.containerTypeAt(key), contents)
   }

   fun containerTypeAt(pos: BlockPos): String {
      return if (ClientAccess.collectiveMinecraft.level!!.getBlockState(pos).block is BarrelBlock) "barrel" else "chest"
   }

   fun dimension(): String {
      return if (ClientAccess.collectiveMinecraft.level == null)
         "unknown"
      else
         ClientAccess.collectiveMinecraft.level!!.dimension().identifier().toString()
   }

   fun nav(): ChestNavigator {
      if (this.nav == null) this.nav = ChestNavigator()
      return this.nav!!
   }

   fun store(): SorterWorldStore {
      if (this.worldStore == null) {
         this.worldStore = SorterWorldStore()
         this.worldStore!!.load()
      }
      return this.worldStore!!
   }

   fun sourceZones(): List<SortZone> {
      return this.store().sourceZones(this.dimension())
   }

   fun timedOut(ticks: Int): Boolean {
      return this.stateTicks > ticks
   }

   fun strangerNearby(): Boolean {
      if (this.isWorldUnavailable()) return false
      for (player in ClientAccess.collectiveMinecraft.level!!.players()) {
         if (player === ClientAccess.collectiveMinecraft.player!! || player.isSpectator) continue
         if (ClientContext.socialRegistry.isFriendPlayer(player)) continue
         if (player.distanceToSqr(ClientAccess.collectiveMinecraft.player!!) < 64) return true
      }
      return false
   }

   // ------------------------------------------------------------------
   //  Зона и метки — настраиваются командой
   // ------------------------------------------------------------------

   /** Ставит первый угол зоны по блоку, на который смотрит игрок. */
   fun setSelectionCorner(): Boolean {
      val looked = this.lookedAtBlock()
      if (looked == null) {
         this.printInfo("Посмотри на угол сундука и повтори.")
         return false
      }
      if (this.selectionCorner1 == null) {
         this.selectionCorner1 = looked
         this.printInfo("Первый угол: §f" + looked.toShortString() + "§f. Посмотри на второй и повтори.")
         return true
      }

      val c1 = this.selectionCorner1
      this.selectionCorner1 = null
      this.store().addZone(SortZone("stash", SortZone.ZoneRole.SOURCE, this.dimension(), c1!!, looked))
      this.printInfo("Зона задана: §f" +
         this.store().sourceZones(this.dimension()).get(this.store().sourceZones(this.dimension()).size - 1).footprint() +
         "§f, углы §f" + c1.toShortString() + "§f — §f" + looked.toShortString() + "§f.")
      return true
   }

   fun clearZones(): Int {
      val removed = this.store().clearSourceZones(this.dimension())
      this.selectionCorner1 = null
      return removed
   }

   /** Помечает сундук как дом для того, чего в нём больше всего. */
   fun pinLookedChest(): Boolean {
      val looked = this.lookedAtBlock()
      if (looked == null || this.isWorldUnavailable()) {
         this.printInfo("Посмотри на сундук и повтори.")
         return false
      }

      val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, looked)
      val data = ContainerIndexManager.getData()
      var tc: TrackedContainer? = data.getContainer(canonical, this.dimension())
      if (tc == null) {
         val other = ContainerGeometry.otherHalf(ClientAccess.collectiveMinecraft.level!!, canonical)
         if (other != null) tc = data.getContainer(other, this.dimension())
      }
      if (tc == null) {
         this.printInfo("Этот сундук не открывали — индекса нет. Открой его один раз.")
         return false
      }

      val group = this.groupOf(this.dominantKey(tc))
      this.store().setPin(canonical, this.dimension(), group)
      this.printInfo("Метка: §f" + canonical.toShortString() + "§f — дом для «§f" + SortGroupKey.friendlyName(group) + "§f».")
      return true
   }

   fun clearLookedPin(): Boolean {
      val looked = this.lookedAtBlock()
      if (looked == null || this.isWorldUnavailable()) return false
      val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, looked)
      if (!this.store().clearPin(canonical, this.dimension())) {
         this.printInfo("Метки на этом сундуке нет.")
         return false
      }
      this.printInfo("Метка снята: §f" + canonical.toShortString())
      return true
   }

   private fun dominantKey(tc: TrackedContainer): String {
      var best = SortGroupKey.MIXED_ITEMS
      var bestCount = -1
      for (e in tc.getTopLevelSignatureCounts().entries) {
         if (e.value > bestCount) {
            best = e.key
            bestCount = e.value
         }
      }
      return best
   }

   fun lookedAtBlock(): BlockPos? {
      if (ClientAccess.collectiveMinecraft.player == null || ClientAccess.collectiveMinecraft.hitResult == null) return null
      if (ClientAccess.collectiveMinecraft.hitResult!!.getType() != HitResult.Type.BLOCK) return null
      return (ClientAccess.collectiveMinecraft.hitResult!! as BlockHitResult).blockPos
   }

   // ------------------------------------------------------------------
   //  Управление прогоном
   // ------------------------------------------------------------------

   fun startSorting() {
      if (this.sourceZones().isEmpty()) {
         this.printInfo("Сначала задай зону: §7.сорт зона§f.")
         return
      }
      if (!this.isToggled()) this.setToggled(true)
      this.resetRun()
      this.state = SortState.PLAN
   }

   fun stopSorting() {
      this.closeOpenContainer()
      if (this.nav != null) {
         this.nav!!.restorePathing()
         this.nav!!.stop()
      }
      this.state = SortState.IDLE
      if (this.isToggled()) this.setToggled(false)
   }

   fun pauseSorting() {
      if (this.state == SortState.IDLE || this.state == SortState.PAUSED) return
      this.enterPause(PauseReason.MANUAL, "Остановлено вручную")
   }

   fun resumeSorting() {
      if (this.state != SortState.PAUSED) return
      this.closeOpenContainer()
      this.nav?.stop()
      this.state = if (this.cargoScan().isEmpty()) SortState.SELECT_JOB else SortState.SELECT_DEST
      this.stateTicks = 0
      this.lastState = this.state
   }

   fun isPaused(): Boolean {
      return this.state == SortState.PAUSED
   }

   fun isSorting(): Boolean {
      return this.isToggled() && this.state != SortState.IDLE
   }

   /** Сколько зон задано в текущем измерении. */
   fun zoneCount(): Int {
      return this.sourceZones().size
   }

   fun statusLine(): String {
      return when (this.state) {
         SortState.IDLE -> "ждёт команды"
         SortState.PLAN -> "планирует"
         SortState.SELECT_JOB -> "выбирает сундук"
         SortState.NAV_SOURCE -> "идёт к источнику"
         SortState.OPEN_SOURCE -> "открывает источник"
         SortState.VERIFY_SOURCE -> "проверяет источник"
         SortState.WITHDRAW -> "забирает"
         SortState.CLOSE_SOURCE -> "закрывает источник"
         SortState.SELECT_DEST -> "выбирает получателя"
         SortState.NAV_DEST -> "идёт к получателю"
         SortState.OPEN_DEST -> "открывает получателя"
         SortState.VERIFY_DEST -> "проверяет получателя"
         SortState.DEPOSIT -> "выкладывает"
         SortState.CLOSE_DEST -> "закрывает получателя"
         SortState.DONE -> "завершает"
         SortState.PAUSED -> "на паузе"
      }
   }

   // ------------------------------------------------------------------
   //  Оверлей
   // ------------------------------------------------------------------

   @EventHandler
   private fun onRenderWorld(event: WorldRenderEvent.Main) {
      if (this.isWorldUnavailable()) return

      if (this.showZones.getValue()) {
         val c = this.zoneColor.getValue()
         if (c.alpha > 5) {
            for (z in this.store().sourceZones(this.dimension())) {
               // Высоты берём из самой зоны: у World в 1.21.11 их больше не отдать
               val box = z.box(minOf(z.y1, z.y2), maxOf(z.y1, z.y2))
               WorldBoxRenderer.fillBox(event.poseStack!!, box,
                  Color(c.red, c.green, c.blue, minOf(40, c.alpha)))
               WorldBoxRenderer.outlineBox(event.poseStack!!, box, c.hashCode(), 1.0f)
            }
         }
      }

      if (!this.showLabels.getValue()) return
      if (this.labelColor.getValue().alpha <= 5 && this.pinnedColor.getValue().alpha <= 5) return

      val rangeSq = this.overlayRange.getValue().toDouble() * this.overlayRange.getValue()
      this.updateOverlayCache()

      for (entry in this.overlayCache) {
         val dist = entry.pos.distSqr(ClientAccess.collectiveMinecraft.player!!.blockPosition())
         if (dist > rangeSq) continue

         val color = if (entry.pinned) this.pinnedColor.getValue() else this.labelColor.getValue()
         if (color.alpha <= 5) continue

         val box = AABB(entry.pos.x + 0.1, entry.pos.y + 0.1, entry.pos.z + 0.1,
            entry.pos.x + 0.9, entry.pos.y + 0.9, entry.pos.z + 0.9)
         WorldBoxRenderer.fillBox(event.poseStack!!, box,
            Color(color.red, color.green, color.blue, minOf(60, color.alpha)))
         WorldBoxRenderer.outlineBox(event.poseStack!!, box, color.hashCode(), 1.0f)
      }
   }

   private fun updateOverlayCache() {
      val now = System.currentTimeMillis()
      val dim = this.dimension()
      if (now - this.overlayCacheTime < 1000L && dim == this.overlayCacheDim) return
      this.overlayCacheTime = now
      this.overlayCacheDim = dim

      val data = ContainerIndexManager.getData()
      val areas = this.sourceZones()
      this.overlayCache.clear()
      if (areas.isEmpty()) return

      for (tc in data.getAllContainers(dim)) {
         val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, tc.position)
         val pinKey = this.store().pinFor(canonical, dim)
         val inArea = this.inArea(areas, tc.position, canonical)
         this.overlayCache.add(OverlayEntry(canonical, pinKey != null))
      }
   }

   // ------------------------------------------------------------------
   //  Сообщения
   // ------------------------------------------------------------------

   fun printInfo(text: String) {
      this.printChat("§f" + text, ChatUtils.MessagePriority.LOW)
   }

   fun printWarning(text: String) {
      this.printChat("§e" + text, ChatUtils.MessagePriority.MID)
   }

   private fun printChat(text: String, priority: ChatUtils.MessagePriority) {
      val message: MutableComponent = Component.literal(MESSAGE_PREFIX + text)
      ChatUtils.printChat(message, ChatUtils.createSignature(-2), priority)
   }

   companion object {
      /** Сколько раз пробуем открыть сундук, прежде чем бросить его. */
      const val MAX_RETRIES = 3
      /** Сколько полных проходов по стешу: один проход не всё разбирает. */
      const val MAX_PASSES = 4
      const val OPEN_ALIGN_TIMEOUT = 40
      private const val MESSAGE_PREFIX = "§7[§bStashSorter§7] §f"
   }
}
