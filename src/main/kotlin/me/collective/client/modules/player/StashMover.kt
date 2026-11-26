package me.collective.client.modules.player

import java.util.ArrayList
import java.util.Comparator
import java.util.HashSet
import java.util.UUID
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
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
import me.collective.client.settings.StringSetting
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.sort.ChestNavigator
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.tracker.ContainerIndexManager
import me.collective.client.utils.tracker.TrackedContainer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.EnderChestBlock
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color

/**
 * StashMover (порт из BepHax Community) — перенос вещей между двумя зонами стеша.
 *
 * <p>Смысл: в стеше нельзя выйти в другую точку с полным инвентарём. Модуль
 * забирает вещи из сундуков зоны входа в инвентарь, телепортируется к зоне
 * выхода и выкладывает их туда. Обратно — то же самое.
 *
 * <p>Способы переноса три, все через серверные команды:
 * <ul>
 *   <li>{@code /kill} — смерть и респавн в точке респавна;</li>
 *   <li>смерть с подходом к месту смерти — респавн там, где стоял;</li>
 *   <li>жемчужина — команда плагина, телепорт к другому игроку.</li>
 * </ul>
 *
 * <p>Отличие от оригинала: у него жемчужинный транспорт доводит до цели
 * отдельной цепочкой состояний RESET_PEARL_* — она «взводит» шалкер и
 * кидает жемчужину, чтобы вернуться. Здесь эта цепочка повторяет порядок
 * действий, но тайминги заданы настройками и без проверки стазиса на стороне
 * сервера. Остальные два способа переноса сделаны как в оригинале.
 *
 * <p>Зоны задаются командой {@code .перенос}: смотри на угол и введи
 * {@code .перенос вход 1} и т.д.
 */
class StashMover : Module("StashMover", "Перенос вещей между двумя зонами стеша", ModuleCategory.PLAYER, "stashmover") {
   private val containerReachSetting: Setting<Float>
   private val maxRetriesSetting: Setting<Int>
   private val onlyShulkersSetting: Setting<Boolean>
   private val breakEmptySetting: Setting<Boolean>
   private val fillEnderChestSetting: Setting<Boolean>

   private val inputAreaPos1: Setting<String>
   private val inputAreaPos2: Setting<String>
   private val outputAreaPos1: Setting<String>
   private val outputAreaPos2: Setting<String>

   private val forwardMethodSetting: Setting<TransportMethod>
   private val forwardKillCommandSetting: Setting<String>
   private val forwardKillRandomSetting: Setting<Boolean>

   private val pearlPlayerSetting: Setting<String>
   private val pearlCommandSetting: Setting<String>
   private val pearlTimeoutSetting: Setting<Int>

   private val goBackMethodSetting: Setting<TransportMethod>
   private val goBackPlayerSetting: Setting<String>
   private val goBackCommandSetting: Setting<String>
   private val goBackKillCommandSetting: Setting<String>
   private val goBackKillRandomSetting: Setting<Boolean>

   private val openDelaySetting: Setting<Int>
   private val transferDelaySetting: Setting<Int>
   private val moveDelaySetting: Setting<Int>
   private val clicksPerTickSetting: Setting<Int>

   private val renderSelectionSetting: Setting<Boolean>
   private val inputColorSetting: Setting<Color>
   private val outputColorSetting: Setting<Color>
   private val activeColorSetting: Setting<Color>

   // --- состояние прогона ---
   private var state = ProcessState.IDLE
   private var lastState = ProcessState.IDLE
   private var stateTimer = 0
   private var ticks = 0
   private var runRequested = false

   private val inputContainers: MutableList<ContainerInfo> = ArrayList()
   private val outputContainers: MutableList<ContainerInfo> = ArrayList()
   private var currentContainer: ContainerInfo? = null
   private var nav: ChestNavigator? = null

   private var goingToInput = false
   private var transportRetries = 0
   private var lastTransportMessage: Long = 0
   private var positionBeforeTransport: Vec3? = null
   private var openFailures = 0
   private var itemsTransferred = 0
   private var awaitingRespawn = false
   private var awaitingPearl = false
   private var awaitingEnderChest = false
   private var settleTicks = 0
   private var alignTicks = 0
   private var startedAt: Long = 0
   private val flagged: MutableSet<BlockPos> = HashSet()
   // Вердикты «пуст/пропущен» переживают пересканирование зоны: без них
   // detectContainers воскрешает разобранные сундуки и цикл не завершается.
   private val deadContainers: MutableSet<BlockPos> = HashSet()
   private var enderChestFillFailures = 0
   private var breakingStartedPos: BlockPos? = null
   private var inventoryBaseline: Array<ItemStack>? = null

   init {
      this.containerReachSetting = this.add(NumberSetting<Float>("Reach", 4.0f, 2.0f, 6.0f).withUnit("m"))
      this.maxRetriesSetting = this.add(NumberSetting<Int>("Retries", 5, 1, 20))
      this.onlyShulkersSetting = this.add(BooleanSetting("OnlyShulkers", true))
      this.breakEmptySetting = this.add(BooleanSetting("BreakEmpty", true))
      this.fillEnderChestSetting = this.add(BooleanSetting("FillEnderChest", true))

      this.inputAreaPos1 = this.add(StringSetting("InputCorner1", ""))
      this.inputAreaPos2 = this.add(StringSetting("InputCorner2", ""))
      this.outputAreaPos1 = this.add(StringSetting("OutputCorner1", ""))
      this.outputAreaPos2 = this.add(StringSetting("OutputCorner2", ""))

      this.forwardMethodSetting = this.add(EnumSetting<TransportMethod>("ForwardMethod", TransportMethod.PEARL))
      this.forwardKillCommandSetting = this.add(StringSetting("ForwardKillCommand", "/kill"))
      this.forwardKillRandomSetting = this.add(BooleanSetting("ForwardKillRandom", true))

      this.pearlPlayerSetting = this.add(StringSetting("PearlPlayer", "PlayerName"))
      this.pearlCommandSetting = this.add(StringSetting("PearlCommand", "pearl"))
      this.pearlTimeoutSetting = this.add(NumberSetting<Int>("PearlTimeout", 10, 1, 60).withUnit("s"))

      this.goBackMethodSetting = this.add(EnumSetting<TransportMethod>("BackMethod", TransportMethod.PEARL))
      this.goBackPlayerSetting = this.add(StringSetting("BackPlayer", "PlayerName"))
      this.goBackCommandSetting = this.add(StringSetting("BackCommand", "back"))
      this.goBackKillCommandSetting = this.add(StringSetting("BackKillCommand", "/kill"))
      this.goBackKillRandomSetting = this.add(BooleanSetting("BackKillRandom", true))

      this.openDelaySetting = this.add(NumberSetting<Int>("OpenDelay", 30, 0, 200))
      this.transferDelaySetting = this.add(NumberSetting<Int>("TransferDelay", 10, 0, 200))
      this.moveDelaySetting = this.add(NumberSetting<Int>("MoveDelay", 5, 0, 60))
      this.clicksPerTickSetting = this.add(NumberSetting<Int>("ClicksPerTick", 6, 1, 27))

      this.renderSelectionSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())
      this.inputColorSetting = this.add(ColorSetting("InputColor", Color(0, 255, 0, 140)).dependsOn(this.renderSelectionSetting))
      this.outputColorSetting = this.add(ColorSetting("OutputColor", Color(0, 100, 255, 140)).dependsOn(this.renderSelectionSetting))
      this.activeColorSetting = this.add(ColorSetting("ActiveColor", Color(255, 255, 0, 150)).dependsOn(this.renderSelectionSetting))
   }

   enum class TransportMethod(private val displayName: String) : NamedEntry {
      PEARL("Жемчужина"),
      KILL("Команда смерти"),
      KILL_POSITION("Смерть и подход");

      override fun getName(): String {
         return this.displayName
      }
   }

   private enum class ProcessState {
      IDLE,
      CHECKING_LOCATION,
      INPUT_PROCESS,
      OUTPUT_PROCESS,
      MOVING_TO_CONTAINER,
      OPENING_CONTAINER,
      TRANSFERRING_ITEMS,
      CLOSING_CONTAINER,
      BREAKING_CONTAINER,
      OPENING_ENDERCHEST,
      FILLING_ENDERCHEST,
      EMPTYING_ENDERCHEST,
      LOADING_PEARL,
      GOING_BACK,
      KILL_COMMAND,
      KILL_POSITION_WALK,
      KILL_POSITION_WAIT
   }

   /** Сундук в зоне: где стоит, сколько слотов и занято. */
   private class ContainerInfo(
      val pos: BlockPos,
      val type: String,
      val totalSlots: Int,
      var usedSlots: Int,
      var empty: Boolean
   ) {
      var skipped = false
   }

   override fun onEnable() {
      this.nav = ChestNavigator()
      this.resetRun()
      this.runRequested = true
   }

   override fun onDisable() {
      if (this.nav != null) {
         this.nav!!.restorePathing()
         this.nav!!.stop()
      }
      this.closeContainer()
      this.resetRun()
      this.runRequested = false
   }

   private fun resetRun() {
      this.state = ProcessState.IDLE
      this.lastState = ProcessState.IDLE
      this.stateTimer = 0
      this.ticks = 0
      this.currentContainer = null
      this.inputContainers.clear()
      this.outputContainers.clear()
      this.awaitingEnderChest = false
      this.settleTicks = 0
      this.alignTicks = 0
      this.goingToInput = false
      this.transportRetries = 0
      this.awaitingRespawn = false
      this.awaitingPearl = false
      this.openFailures = 0
      this.itemsTransferred = 0
      this.flagged.clear()
      this.deadContainers.clear()
      this.enderChestFillFailures = 0
      this.breakingStartedPos = null
      this.inventoryBaseline = null
      this.startedAt = System.currentTimeMillis()
   }

   @EventHandler
   private fun onTick(event: LocalPlayerTickEvent) {
      if (!this.runRequested || this.isWorldUnavailable()) {
         if (this.isWorldUnavailable() && !this.inputContainers.isEmpty()) {
            // сменился мир — навигатор и зоны больше не валидны
            this.nav?.stop()
            this.resetRun()
            this.runRequested = false
            this.setToggled(false)
         }
         return
      }
      this.ticks++

      if (this.inventoryBaseline == null) {
         this.inventoryBaseline = Array(INVENTORY_SIZE) { i: Int ->
            ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i).copy()
         }
      }

      if (!this.hasValidAreas()) {
         if (this.ticks % 100 == 1) {
            this.printWarning("Зоны не заданы. Смотри на угол: §7.перенос вход 1§f, §7.перенос вход 2§f, потом §7.перенос выход 1§f и §7.перенос выход 2§f.")
         }
         return
      }

      if (this.state != this.lastState) {
         this.lastState = this.state
         this.stateTimer = 0
      } else {
         this.stateTimer++
      }

      if (ClientAccess.collectiveMinecraft.player!!.isDeadOrDying()) {
         this.handleDeath()
         return
      }

      when (this.state) {
         ProcessState.IDLE -> this.state = ProcessState.CHECKING_LOCATION
         ProcessState.CHECKING_LOCATION -> this.checkLocation()
         ProcessState.INPUT_PROCESS -> this.handleInputProcess()
         ProcessState.OUTPUT_PROCESS -> this.handleOutputProcess()
         ProcessState.MOVING_TO_CONTAINER -> this.handleMovingToContainer()
         ProcessState.OPENING_CONTAINER -> this.handleOpeningContainer()
         ProcessState.TRANSFERRING_ITEMS -> this.handleTransferring()
         ProcessState.CLOSING_CONTAINER -> this.handleClosingContainer()
         ProcessState.BREAKING_CONTAINER -> this.handleBreakingContainer()
         ProcessState.OPENING_ENDERCHEST -> this.handleOpeningEnderChest()
         ProcessState.FILLING_ENDERCHEST -> this.handleFillingEnderChest()
         ProcessState.EMPTYING_ENDERCHEST -> this.handleEmptyingEnderChest()
         ProcessState.KILL_COMMAND -> this.handleKillCommand()
         ProcessState.KILL_POSITION_WALK -> this.handleKillPositionWalk()
         ProcessState.KILL_POSITION_WAIT -> this.handleKillPositionWait()
         ProcessState.GOING_BACK -> this.handlePearlGoBack()
         ProcessState.LOADING_PEARL -> this.handlePearlForward()
         else -> {
         }
      }
   }

   // ------------------------------------------------------------------
   //  Зоны
   // ------------------------------------------------------------------

   fun setAreaCorner(input: Boolean, corner: Int, pos: BlockPos?): Boolean {
      if (pos == null || (corner != 1 && corner != 2)) return false
      if (input && corner == 1) {
         this.inputAreaPos1.setValue(encode(pos))
      } else if (input) {
         this.inputAreaPos2.setValue(encode(pos))
      } else if (corner == 1) {
         this.outputAreaPos1.setValue(encode(pos))
      } else {
         this.outputAreaPos2.setValue(encode(pos))
      }
      this.nav?.stop()
      this.resetRun()
      return true
   }

   /** Блок, на который смотрит игрок, — для команды .перенос. */
   fun lookedAtBlock(): BlockPos? {
      if (ClientAccess.collectiveMinecraft.player == null || ClientAccess.collectiveMinecraft.hitResult == null) return null
      if (ClientAccess.collectiveMinecraft.hitResult!!.getType() != HitResult.Type.BLOCK) return null
      return (ClientAccess.collectiveMinecraft.hitResult!! as BlockHitResult).blockPos
   }

   fun clearAreas(): Int {
      this.inputAreaPos1.setValue("")
      this.inputAreaPos2.setValue("")
      this.outputAreaPos1.setValue("")
      this.outputAreaPos2.setValue("")
      this.nav?.stop()
      this.resetRun()
      return 1
   }

   private fun hasValidAreas(): Boolean {
      return this.inputCorner1() != null && this.inputCorner2() != null &&
         this.outputCorner1() != null && this.outputCorner2() != null
   }

   private fun inputCorner1(): BlockPos? {
      return decode(this.inputAreaPos1.getValue())
   }

   private fun inputCorner2(): BlockPos? {
      return decode(this.inputAreaPos2.getValue())
   }

   private fun outputCorner1(): BlockPos? {
      return decode(this.outputAreaPos1.getValue())
   }

   private fun outputCorner2(): BlockPos? {
      return decode(this.outputAreaPos2.getValue())
   }

   private fun inArea(a: BlockPos?, b: BlockPos?, p: BlockPos): Boolean {
      val range = 20
      return p.x >= Math.min(a!!.x, b!!.x) - range && p.x <= Math.max(a.x, b.x) + range &&
         p.z >= Math.min(a.z, b.z) - range && p.z <= Math.max(a.z, b.z) + range
   }

   private fun nearInput(): Boolean {
      return this.inArea(this.inputCorner1(), this.inputCorner2(), ClientAccess.collectiveMinecraft.player!!.blockPosition())
   }

   private fun nearOutput(): Boolean {
      return this.inArea(this.outputCorner1(), this.outputCorner2(), ClientAccess.collectiveMinecraft.player!!.blockPosition())
   }

   // ------------------------------------------------------------------
   //  Обнаружение сундуков
   // ------------------------------------------------------------------

   private fun detectContainers(input: Boolean) {
      val c1 = if (input) this.inputCorner1() else this.outputCorner1()
      val c2 = if (input) this.inputCorner2() else this.outputCorner2()
      val target: MutableList<ContainerInfo> = if (input) this.inputContainers else this.outputContainers
      // Сохраняем вердикты прошлого прохода ДО очистки списка, иначе
      // пересканирование «воскрешает» пустые/пропущенные сундуки.
      for (c in target) {
         if (c.empty || c.skipped) this.deadContainers.add(c.pos)
      }
      target.clear()
      this.flagged.clear()

      val minX = Math.min(c1!!.x, c2!!.x) - 20
      val maxX = Math.max(c1.x, c2.x) + 20
      val minZ = Math.min(c1.z, c2.z) - 20
      val maxZ = Math.max(c1.z, c2.z) + 20
      val dim = ClientAccess.collectiveMinecraft.level!!.dimension().identifier().toString()

      for (cx in (minX shr 4)..(maxX shr 4)) {
         for (cz in (minZ shr 4)..(maxZ shr 4)) {
            if (!ClientAccess.collectiveMinecraft.level!!.chunkSource.hasChunk(cx, cz)) continue
            val chunk = ClientAccess.collectiveMinecraft.level!!.getChunk(cx, cz, ChunkStatus.FULL, false) as LevelChunk?
            if (chunk == null) continue

            for (be in chunk.blockEntities.values) {
               if (!ContainerGeometry.isContainerBlock(be.blockState)) continue
               val canonical = ContainerGeometry.canonical(ClientAccess.collectiveMinecraft.level!!, be.blockPos)
               if (!this.flagged.add(canonical)) continue
               if (canonical.x < minX || canonical.x > maxX ||
         canonical.z < minZ || canonical.z > maxZ) continue

               // Занятость берём из индекса контейнеров (если сундук открывали),
               // иначе считаем сундук полным набором слотов
               val tracked: TrackedContainer? = ContainerIndexManager.getData().getContainer(canonical, dim)
               val used = tracked?.usedStacks ?: 0
               val type = tracked?.containerType ?: this.containerTypeAt(canonical)
               val info = ContainerInfo(canonical, type,
                  ContainerGeometry.slotCount(ClientAccess.collectiveMinecraft.level!!, canonical, type, used), used, tracked != null && used == 0)
               if (this.deadContainers.contains(canonical)) {
                  info.empty = true
                  info.skipped = true
               }
               target.add(info)
            }
         }
      }
   }

   private fun containerTypeAt(pos: BlockPos): String {
      val block = ClientAccess.collectiveMinecraft.level!!.getBlockState(pos).block.getName().getString()
      if (block.contains("barrel")) return "barrel"
      if (block.contains("trapped")) return "trapped_chest"
      return "chest"
   }

   // ------------------------------------------------------------------
   //  Поток
   // ------------------------------------------------------------------

   private fun checkLocation() {
      if (this.nearInput()) {
         this.goingToInput = true
         this.detectContainers(true)
         this.printInfo("У входа найдено сундуков: §f" + this.inputContainers.size)
         this.state = ProcessState.INPUT_PROCESS
      } else if (this.nearOutput()) {
         this.goingToInput = false
         this.detectContainers(false)
         this.printInfo("У выхода найдено сундуков: §f" + this.outputContainers.size)
         this.state = ProcessState.OUTPUT_PROCESS
         this.stateTimer = 10
      } else {
         if (this.stateTimer % 100 == 0) this.printWarning("Игрок не у зон, жду. Подойди к зоне входа или выхода.")
      }
   }

   private fun handleInputProcess() {
      if (this.inventoryFull()) {
         // Сколько раз пробуем слить груз в эндер-сундук за один заход:
         // без ограничения цикл INPUT_PROCESS → OPENING_ENDERCHEST →
         // FILLING_ENDERCHEST → INPUT_PROCESS не завершается, когда
         // эндер-сундук тоже полон, а фолбэк на транспорт недостижим.
         if (this.fillEnderChestSetting.getValue() && this.enderChestFillFailures < ENDER_CHEST_FILL_ATTEMPTS) {
            this.state = ProcessState.OPENING_ENDERCHEST
         } else {
            this.startTransport(false)
         }
         return
      }
      this.enderChestFillFailures = 0

      this.currentContainer = this.nearestWithItems(this.inputContainers)
      if (this.currentContainer == null) {
         this.detectContainers(true)
         this.currentContainer = this.nearestWithItems(this.inputContainers)
      }

      if (this.currentContainer == null) {
         this.startTransport(false)
      } else {
         this.state = ProcessState.MOVING_TO_CONTAINER
      }
   }

   private fun handleOutputProcess() {
      if (!this.hasCargo()) {
         this.startTransport(true)
         return
      }

      this.currentContainer = this.nearestUsableOutput()
      if (this.currentContainer == null) {
         this.detectContainers(false)
         this.currentContainer = this.nearestUsableOutput()
      }

      if (this.currentContainer == null) {
         this.printWarning("Нет сундука для выгрузки, жду. Проверь зону выхода и что сундуки не заняты.")
         this.stateTimer = 0
         return
      }
      this.state = ProcessState.MOVING_TO_CONTAINER
   }

   private fun nearestWithItems(list: List<ContainerInfo>): ContainerInfo? {
      return list.stream()
         .filter { c: ContainerInfo -> !c.empty && !c.skipped }
         .min(Comparator.comparingDouble { c: ContainerInfo -> ClientAccess.collectiveMinecraft.player!!.blockPosition().distSqr(c.pos) })
         .orElse(null)
   }

   private fun nearestUsableOutput(): ContainerInfo? {
      return this.outputContainers.stream()
         .filter { c: ContainerInfo -> !c.skipped && c.usedSlots < c.totalSlots }
         .min(Comparator.comparingDouble { c: ContainerInfo -> ClientAccess.collectiveMinecraft.player!!.blockPosition().distSqr(c.pos) })
         .orElse(null)
   }

   private fun handleMovingToContainer() {
      val container = this.currentContainer
      if (container == null) {
         this.state = if (this.goingToInput) ProcessState.INPUT_PROCESS else ProcessState.OUTPUT_PROCESS
         return
      }
      if (ClientAccess.collectiveMinecraft.level!!.hasChunkAt(container.pos) &&
         !ContainerGeometry.isContainerBlock(ClientAccess.collectiveMinecraft.level!!.getBlockState(container.pos))) {
         container.skipped = true
         this.currentContainer = null
         return
      }

      val status = this.nav().travelTo(container.pos)
      if (status == ChestNavigator.Status.ARRIVED) {
         this.state = ProcessState.OPENING_CONTAINER
         this.stateTimer = this.moveDelaySetting.getValue()
      } else if (status == ChestNavigator.Status.UNREACHABLE) {
         this.note("не дойти", container.pos, this.nav().failure())
         container.skipped = true
         this.currentContainer = null
         this.state = if (this.goingToInput) ProcessState.INPUT_PROCESS else ProcessState.OUTPUT_PROCESS
      } else if (this.stateTimer > 2000) {
         this.note("не дошёл за отведённое время", container.pos, "")
         container.skipped = true
         this.currentContainer = null
         this.state = if (this.goingToInput) ProcessState.INPUT_PROCESS else ProcessState.OUTPUT_PROCESS
      }
   }

   private fun handleOpeningContainer() {
      val container = this.currentContainer ?: return

      val hit = this.nav().openHit(container.pos)
      if (hit == null) {
         if (++this.openFailures > OPEN_RETRIES) {
            this.note("не открывается", container.pos, "")
            container.skipped = true
            this.currentContainer = null
            this.state = if (this.goingToInput) ProcessState.INPUT_PROCESS else ProcessState.OUTPUT_PROCESS
         } else {
            this.state = ProcessState.MOVING_TO_CONTAINER
         }
         return
      }

      if (!this.settled()) {
         this.alignTicks = 0
         return
      }
      if (!this.turnTowards(hit.location)) {
         this.alignTicks = 0
         return
      }
      if (this.alignTicks++ < 1) return

      ClientAccess.collectiveMinecraft.gameMode!!.useItemOn(ClientAccess.collectiveMinecraft.player!!, InteractionHand.MAIN_HAND, hit)
      ClientAccess.collectiveMinecraft.player!!.swing(InteractionHand.MAIN_HAND)
      this.stateTimer = this.openDelaySetting.getValue()
      // Экран откроется только через несколько тиков, поэтому проверять его
      // здесь бессмысленно: счётчик неудач растёт в handleTransferring.
      this.state = ProcessState.TRANSFERRING_ITEMS
   }

   private fun handleTransferring() {
      if (this.stateTimer < this.transferDelaySetting.getValue()) return

      if (ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>) {
         // экран закрылся сам — пробуем открыть заново
         if (this.currentContainer?.empty == false && ++this.openFailures <= OPEN_RETRIES) {
            this.state = ProcessState.MOVING_TO_CONTAINER
         } else {
            this.state = ProcessState.CLOSING_CONTAINER
         }
         return
      }
      val containerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      if (containerMenu !is ChestMenu) {
         this.state = ProcessState.CLOSING_CONTAINER
         return
      }

      val total = containerMenu.rowCount * 9
      if (this.goingToInput) {
         // забираем груз из сундука в инвентарь
         var moved = 0
         var i = 0
         while (i < total && moved < this.clicksPerTickSetting.getValue()) {
            val stack = containerMenu.getSlot(i).item
            if (!stack.isEmpty && this.isCargo(stack)) {
               ClientAccess.collectiveMinecraft.gameMode!!.handleContainerInput(containerMenu.containerId, i, 0, ContainerInput.QUICK_MOVE, ClientAccess.collectiveMinecraft.player!!)
               this.itemsTransferred++
               moved++
            }
            i++
         }

         if (moved == 0) {
            this.currentContainer!!.empty = true
            this.currentContainer!!.usedSlots = 0
            this.state = ProcessState.CLOSING_CONTAINER
         } else if (this.inventoryFull()) {
            this.state = ProcessState.CLOSING_CONTAINER
         }
      } else {
         // выкладываем груз из инвентаря в сундук
         var free = 0
         for (i in 0 until total) if (containerMenu.getSlot(i).item.isEmpty) free++

         if (free == 0) {
            this.note("полный получатель", if (this.currentContainer == null) null else this.currentContainer!!.pos, "")
            this.currentContainer!!.skipped = true
            this.currentContainer = null
            this.state = ProcessState.CLOSING_CONTAINER
            return
         }

         var moved = 0
         var i = 0
         while (i < INVENTORY_SIZE && moved < this.clicksPerTickSetting.getValue() && moved < free) {
            val stack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i)
            if (!stack.isEmpty && this.isCargo(stack)) {
               ClientAccess.collectiveMinecraft.gameMode!!.handleContainerInput(containerMenu.containerId, invIndexToMenuId(containerMenu, i), 0, ContainerInput.QUICK_MOVE, ClientAccess.collectiveMinecraft.player!!)
               moved++
            }
            i++
         }

         if (moved == 0 || !this.hasCargo()) {
            this.currentContainer!!.usedSlots = total - free
            this.state = ProcessState.CLOSING_CONTAINER
         }
      }
   }

   private fun handleClosingContainer() {
      this.closeContainer()
      this.stateTimer = this.transferDelaySetting.getValue()

      if (this.goingToInput) {
         if (this.onlyShulkersSetting.getValue()) this.dropNonCargo()
         if (this.currentContainer?.empty == true && this.breakEmptySetting.getValue()) {
            this.state = ProcessState.BREAKING_CONTAINER
         } else {
            this.state = ProcessState.INPUT_PROCESS
         }
      } else {
         this.state = ProcessState.OUTPUT_PROCESS
      }
   }

   private fun handleBreakingContainer() {
      val container = this.currentContainer
      if (container == null || !this.settled()) {
         this.state = ProcessState.INPUT_PROCESS
         return
      }

      // Таймаут проверяем ДО openHit: иначе при недостижимом сундуке
      // состояние BREAKING_CONTAINER зависает навсегда.
      if (this.stateTimer > BREAK_TIMEOUT_TICKS) {
         this.flagged.add(container.pos)
         this.printWarning("Сундук " + container.pos.toShortString() + " не получилось разобрать, помечаю и пропускаю.")
         container.skipped = true
         this.currentContainer = null
         this.breakingStartedPos = null
         this.state = ProcessState.INPUT_PROCESS
         return
      }

      val level = ClientAccess.collectiveMinecraft.level
      val gameMode = ClientAccess.collectiveMinecraft.gameMode
      if (level == null || gameMode == null) return
      if (!ContainerGeometry.isContainerBlock(level.getBlockState(container.pos))) {
         // блока больше нет — сундук разобран
         this.flagged.add(container.pos)
         this.printInfo("Сундук " + container.pos.toShortString() + " разобран.")
         container.skipped = true
         this.currentContainer = null
         this.breakingStartedPos = null
         this.state = ProcessState.INPUT_PROCESS
         return
      }

      val hit = this.nav().openHit(container.pos)
      if (hit == null || !this.turnTowards(hit.location)) return

      // В выживании одного startDestroyBlock недостаточно: добычу нужно
      // продолжать каждый тик, пока блок не сломается.
      val fallbackDirection: Direction = Direction.UP
      val direction: Direction = BlockInteractionUtils.getStrictDirectionsOrFallback(container.pos, fallbackDirection).first()
      if (this.breakingStartedPos != container.pos) {
         this.breakingStartedPos = container.pos
         gameMode.startDestroyBlock(container.pos, direction)
      } else {
         gameMode.continueDestroyBlock(container.pos, direction)
      }
   }

   private fun handleDeath() {
      if (this.goingToInput) {
         // после смерти у входа просто продолжаем
         this.awaitingRespawn = false
         this.state = ProcessState.KILL_POSITION_WAIT
         this.stateTimer = 0
      }
   }

   // ------------------------------------------------------------------
   //  Эндер-сундук как промежуточная ёмкость
   // ------------------------------------------------------------------

   private fun handleOpeningEnderChest() {
      if (ClientAccess.collectiveMinecraft.gui.screen() !is AbstractContainerScreen<*>) {
         // ищем эндер-сундук рядом, открываем
         val ec = this.findEnderChest()
         if (ec == null) {
            this.printWarning("Эндер-сундук не найден рядом, еду с грузом напрямую.")
            this.startTransport(false)
            return
         }
         val hit = this.nav().openHit(ec)
         if (hit == null || !this.turnTowards(hit.location)) return
         ClientAccess.collectiveMinecraft.gameMode!!.useItemOn(ClientAccess.collectiveMinecraft.player!!, InteractionHand.MAIN_HAND, hit)
         ClientAccess.collectiveMinecraft.player!!.swing(InteractionHand.MAIN_HAND)
         this.stateTimer = this.openDelaySetting.getValue()
         this.awaitingEnderChest = true
      }
      // Эндер-сундук открывается обычным ChestMenu, так что
      // по типу обработчика его не отличить — ориентируемся на флаг клика.
      if (this.awaitingEnderChest && ClientAccess.collectiveMinecraft.gui.screen() is AbstractContainerScreen<*> &&
         ClientAccess.collectiveMinecraft.gui.screen() !is InventoryScreen) {
         this.awaitingEnderChest = false
         this.state = ProcessState.FILLING_ENDERCHEST
      } else if (this.stateTimer > 60) {
         this.printWarning("Эндер-сундук не открылся, еду напрямую.")
         this.startTransport(false)
      }
   }

   private fun handleFillingEnderChest() {
      val containerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      if (containerMenu !is ChestMenu) {
         this.closeContainer()
         this.state = ProcessState.INPUT_PROCESS
         return
      }
      var moved = 0
      var i = 0
      while (i < INVENTORY_SIZE && moved < this.clicksPerTickSetting.getValue()) {
         val stack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i)
         if (!stack.isEmpty && this.isCargo(stack)) {
            ClientAccess.collectiveMinecraft.gameMode!!.handleContainerInput(containerMenu.containerId, invIndexToMenuId(containerMenu, i), 0, ContainerInput.QUICK_MOVE, ClientAccess.collectiveMinecraft.player!!)
            moved++
         }
         i++
      }
      if (moved == 0) {
         // Эндер-сундук ничего не принял (полон) — считаем неудачную попытку
         // и после лимита уходим на транспорт с полным инвентарём.
         this.enderChestFillFailures++
         this.closeContainer()
         this.state = ProcessState.INPUT_PROCESS
      } else {
         this.enderChestFillFailures = 0
      }
   }

   private fun handleEmptyingEnderChest() {
      // обратная ветка: забираем из эндер-сундука, когда у выхода инвентарь полон
      this.handleFillingEnderChest()
   }

   private fun findEnderChest(): BlockPos? {
      val p = ClientAccess.collectiveMinecraft.player!!.blockPosition()
      for (dx in -4..4) {
         for (dy in -2..2) {
            for (dz in -4..4) {
               val q = p.offset(dx, dy, dz)
               if (ClientAccess.collectiveMinecraft.level!!.getBlockState(q).block is EnderChestBlock) {
                  return q
               }
            }
         }
      }
      return null
   }

   // ------------------------------------------------------------------
   //  Перенос
   // ------------------------------------------------------------------

   private fun startTransport(toInput: Boolean) {
      this.goingToInput = toInput
      this.currentContainer = null
      this.closeContainer()
      this.nav().stop()
      this.positionBeforeTransport = ClientAccess.collectiveMinecraft.player!!.position()
      this.transportRetries = 0
      this.awaitingPearl = false
      this.awaitingRespawn = false

      val method = if (toInput) this.goBackMethodSetting.getValue() else this.forwardMethodSetting.getValue()
      when (method) {
         TransportMethod.PEARL -> this.state = if (toInput) ProcessState.GOING_BACK else ProcessState.LOADING_PEARL
         TransportMethod.KILL -> this.state = ProcessState.KILL_COMMAND
         TransportMethod.KILL_POSITION -> this.state = ProcessState.KILL_POSITION_WALK
      }
   }

   private fun handlePearlForward() {
      if (!this.awaitingPearl) {
         this.sendChat(this.pearlCommandSetting.getValue())
         this.awaitingPearl = true
         this.lastTransportMessage = System.currentTimeMillis()
      }
      if (this.positionBeforeTransport != null &&
         ClientAccess.collectiveMinecraft.player!!.position().distanceToSqr(this.positionBeforeTransport!!) > 100.0) {
         this.printInfo("Телепорт к зоне выхода удался.")
         this.onArrived()
         return
      }
      if (System.currentTimeMillis() - this.lastTransportMessage > this.pearlTimeoutSetting.getValue() * 1000L) {
         if (++this.transportRetries > this.maxRetriesSetting.getValue()) {
            this.printError("Жемчужина не сработала за " + this.maxRetriesSetting.getValue() + " попыток. Останавливаюсь.")
            this.awaitingPearl = false
            this.setToggled(false)
         } else {
            this.sendChat(this.pearlCommandSetting.getValue())
            this.lastTransportMessage = System.currentTimeMillis()
         }
      }
   }

   private fun handlePearlGoBack() {
      if (!this.awaitingPearl) {
         this.sendChat(this.goBackCommandSetting.getValue())
         this.awaitingPearl = true
         this.lastTransportMessage = System.currentTimeMillis()
      }
      if (this.positionBeforeTransport != null &&
         ClientAccess.collectiveMinecraft.player!!.position().distanceToSqr(this.positionBeforeTransport!!) > 100.0) {
         this.printInfo("Вернулся к зоне входа.")
         this.onArrived()
         return
      }
      if (System.currentTimeMillis() - this.lastTransportMessage > this.pearlTimeoutSetting.getValue() * 1000L) {
         if (++this.transportRetries > this.maxRetriesSetting.getValue()) {
            this.printError("Обратная жемчужина не сработала. Останавливаюсь.")
            this.awaitingPearl = false
            this.setToggled(false)
         } else {
            this.sendChat(this.goBackCommandSetting.getValue())
            this.lastTransportMessage = System.currentTimeMillis()
         }
      }
   }

   private fun handleKillCommand() {
      if (!this.awaitingRespawn) {
         val base = (if (this.goingToInput) this.goBackKillCommandSetting.getValue() else this.forwardKillCommandSetting.getValue()).trim()
         val random = if (this.goingToInput) this.goBackKillRandomSetting.getValue() else this.forwardKillRandomSetting.getValue()
         var cmd = if (base.startsWith("/")) base.substring(1) else base
         if (random) cmd = cmd + " " + UUID.randomUUID().toString().substring(0, 4)
         this.sendChatCommand(cmd)
         this.awaitingRespawn = true
         this.lastTransportMessage = System.currentTimeMillis()
         this.printInfo("Отправил команду смерти, жду респавна.")
      }
      if (System.currentTimeMillis() - this.lastTransportMessage > 3000L) {
         this.awaitingRespawn = false
         // после /kill игрок появляется у зоны респавна; дальше разбираемся
         if (this.nearInput()) {
            this.onArrived()
         } else if (this.nearOutput()) {
            this.onArrived()
         } else if (++this.transportRetries > this.maxRetriesSetting.getValue()) {
            this.printWarning("После смерти игрок не у зон. Переносы на /kill требуют, чтобы респавн был рядом со стешем.")
            this.setToggled(false)
         } else {
            this.state = ProcessState.KILL_COMMAND
         }
      }
   }

   private fun handleKillPositionWalk() {
      if (ClientAccess.collectiveMinecraft.player!!.isDeadOrDying()) return

      val death = ClientAccess.collectiveMinecraft.player!!.lastDeathLocation
      if (death.isEmpty()) {
         // ещё не умирали — шлём команду
         val base = (if (this.goingToInput) this.goBackKillCommandSetting.getValue() else this.forwardKillCommandSetting.getValue()).trim()
         val cmd = if (base.startsWith("/")) base.substring(1) else base
         this.sendChatCommand(cmd)
         this.state = ProcessState.KILL_POSITION_WAIT
         this.stateTimer = 0
         return
      }

      val target = death.get().pos()
      if (ClientAccess.collectiveMinecraft.player!!.blockPosition().distSqr(target) > 9) {
         if (this.stateTimer > 2000) {
            this.printWarning("Не дошёл до места смерти, сдаюсь.")
            this.setToggled(false)
         }
         return
      }
      this.state = ProcessState.KILL_POSITION_WAIT
      this.stateTimer = 0
   }

   private fun handleKillPositionWait() {
      if (!ClientAccess.collectiveMinecraft.player!!.isDeadOrDying()) {
         if (this.stateTimer > 40) {
            this.printInfo("Респавн у места смерти, продолжаю.")
            this.onArrived()
         }
      } else if (this.stateTimer > 200) {
         this.printWarning("Смерть не отработала.")
         this.setToggled(false)
      }
   }

   private fun onArrived() {
      this.awaitingPearl = false
      this.awaitingRespawn = false
      this.closeContainer()
      if (this.goingToInput) {
         this.detectContainers(true)
         this.state = ProcessState.INPUT_PROCESS
      } else {
         this.detectContainers(false)
         this.state = ProcessState.OUTPUT_PROCESS
         this.stateTimer = 10
      }
   }

   // ------------------------------------------------------------------
   //  Мелочи
   // ------------------------------------------------------------------

   private fun sendChat(message: String) {
      if (ClientAccess.collectiveMinecraft.getConnection() != null && !message.isBlank()) {
         ClientAccess.collectiveMinecraft.getConnection()!!.sendChat(message)
      }
   }

   private fun sendChatCommand(command: String) {
      if (ClientAccess.collectiveMinecraft.getConnection() != null && !command.isBlank()) {
         ClientAccess.collectiveMinecraft.getConnection()!!.sendCommand(command)
      }
   }

   private fun settled(): Boolean {
      this.nav().stop()
      ClientAccess.collectiveMinecraft.options.keyUp.setDown(false)
      ClientAccess.collectiveMinecraft.options.keyDown.setDown(false)
      ClientAccess.collectiveMinecraft.options.keyLeft.setDown(false)
      ClientAccess.collectiveMinecraft.options.keyRight.setDown(false)
      ClientAccess.collectiveMinecraft.options.keyJump.setDown(false)
      ClientAccess.collectiveMinecraft.options.keySprint.setDown(false)
      return this.settleTicks++ >= 2
   }

   private fun turnTowards(point: Vec3): Boolean {
      val eye = ClientAccess.collectiveMinecraft.player!!.getEyePosition()
      val dx = point.x - eye.x
      val dy = point.y - eye.y
      val dz = point.z - eye.z
      val horizontal = Math.sqrt(dx * dx + dz * dz)
      val targetYaw = (Math.toDegrees(Math.atan2(dz, dx)) - 90.0).toFloat()
      val targetPitch = (-Math.toDegrees(Math.atan2(dy, horizontal))).toFloat()

      val dYaw = Mth.wrapDegrees(targetYaw - ClientAccess.collectiveMinecraft.player!!.getYRot())
      val dPitch = targetPitch - ClientAccess.collectiveMinecraft.player!!.getXRot()
      val step = 45.0f
      val yaw = ClientAccess.collectiveMinecraft.player!!.getYRot() + Mth.clamp(dYaw, -step, step)
      ClientAccess.collectiveMinecraft.player!!.setYRot(yaw)
      ClientAccess.collectiveMinecraft.player!!.setYHeadRot(yaw)
      ClientAccess.collectiveMinecraft.player!!.setXRot(
         Mth.clamp(ClientAccess.collectiveMinecraft.player!!.getXRot() + Mth.clamp(dPitch, -step, step), -90.0f, 90.0f))
      return Math.abs(dYaw) <= 3.0f && Math.abs(dPitch) <= 3.0f
   }

   private fun note(reason: String, pos: BlockPos?, detail: String) {
      if (this.ticks % 200 == 0) {
         this.printInfo("Пропуск: " + reason + (if (pos == null) "" else " " + pos.toShortString()) + (if (detail.isEmpty()) "" else " — " + detail) + ".")
      }
   }

   private fun nav(): ChestNavigator {
      if (this.nav == null) this.nav = ChestNavigator()
      return this.nav!!
   }

   private fun closeContainer() {
      val screen = ClientAccess.collectiveMinecraft.gui.screen()
      if (screen != null &&
         screen is AbstractContainerScreen<*> &&
         screen !is InventoryScreen) {
         screen.onClose()
      }
   }

   private fun isCargo(stack: ItemStack): Boolean {
      return !this.onlyShulkersSetting.getValue() || ContainerGeometry.isShulkerItem(stack)
   }

   private fun hasCargo(): Boolean {
      for (i in 0 until INVENTORY_SIZE) {
         val s = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i)
         if (!s.isEmpty && this.isCargo(s)) return true
      }
      return false
   }

   private fun inventoryFull(): Boolean {
      for (i in 0 until INVENTORY_SIZE) {
         if (ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i).isEmpty) return false
      }
      return true
   }

   private fun dropNonCargo() {
      val baseline = this.inventoryBaseline
      for (i in 0 until INVENTORY_SIZE) {
         val s = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(i)
         if (s.isEmpty || this.isCargo(s)) continue
         // Выбрасываем только то, что появилось в инвентаре за прогон
         // (мусор из сундуков); собственные вещи игрока не трогаем.
         val before = if (baseline == null) ItemStack.EMPTY else baseline[i]
         if (!before.isEmpty && ItemStack.isSameItemSameComponents(before, s)) continue
         // Выброс слота без смены хотбара: кладём предмет к игроку вручную,
         // иначе пришлось бы переключать selectedSlot, и сервер увидит
         // несоответствие между хотбаром и инвентарём.
         if (!ClientAccess.collectiveMinecraft.player!!.isSpectator) {
            ClientAccess.collectiveMinecraft.player!!.drop(s.copy(), false)
            ClientAccess.collectiveMinecraft.player!!.inventory.setItem(i, ItemStack.EMPTY)
         }
      }
   }

   // ------------------------------------------------------------------
   //  Отрисовка
   // ------------------------------------------------------------------

   @EventHandler
   private fun onRenderWorld(event: WorldRenderEvent.Main) {
      if (!this.renderSelectionSetting.getValue() || !this.hasValidAreas() ||
         ClientAccess.collectiveMinecraft.player == null) {
         return
      }

      this.drawArea(event, this.inputCorner1(), this.inputCorner2(), this.inputColorSetting.getValue())
      this.drawArea(event, this.outputCorner1(), this.outputCorner2(), this.outputColorSetting.getValue())

      for (c in this.inputContainers) this.drawContainer(event, c, this.inputColorSetting.getValue())
      for (c in this.outputContainers) this.drawContainer(event, c, this.outputColorSetting.getValue())
      if (this.currentContainer != null) this.drawContainer(event, this.currentContainer!!, this.activeColorSetting.getValue())
   }

   private fun drawArea(event: WorldRenderEvent, a: BlockPos?, b: BlockPos?, color: Color) {
      if (color.alpha <= 5) return
      val minY = Math.min(a!!.y, b!!.y)
      val maxY = Math.max(a.y, b.y)
      val box = AABB(Math.min(a.x, b.x) - 20.0, minY.toDouble(), Math.min(a.z, b.z) - 20.0,
         Math.max(a.x, b.x) + 21.0, maxY.toDouble(), Math.max(a.z, b.z) + 21.0)
      WorldBoxRenderer.outlineBox(event.poseStack!!, box, color.hashCode(), 1.0f)
   }

   private fun drawContainer(event: WorldRenderEvent, c: ContainerInfo, color: Color) {
      if (color.alpha <= 5) return
      if (c.pos.distSqr(ClientAccess.collectiveMinecraft.player!!.blockPosition()) > 256) return
      val box = AABB(c.pos.x + 0.1, c.pos.y + 0.1, c.pos.z + 0.1,
         c.pos.x + 0.9, c.pos.y + 0.9, c.pos.z + 0.9)
      WorldBoxRenderer.fillBox(event.poseStack!!, box,
         Color(color.red, color.green, color.blue, minOf(60, color.alpha)))
      WorldBoxRenderer.outlineBox(event.poseStack!!, box, color.hashCode(), 1.0f)
   }

   // ------------------------------------------------------------------
   //  Сообщения
   // ------------------------------------------------------------------

   private fun printInfo(text: String) {
      this.printChat("§f" + text, ChatUtils.MessagePriority.LOW)
   }

   private fun printWarning(text: String) {
      this.printChat("§e" + text, ChatUtils.MessagePriority.MID)
   }

   private fun printError(text: String) {
      this.printChat("§c" + text, ChatUtils.MessagePriority.HIGH)
   }

   private fun printChat(text: String, priority: ChatUtils.MessagePriority) {
      val message: MutableComponent = Component.literal(MESSAGE_PREFIX + text)
      ChatUtils.printChat(message, ChatUtils.createSignature(-2), priority)
   }

   companion object {
      private const val INVENTORY_SIZE = 36
      private const val OPEN_RETRIES = 3
      private const val ENDER_CHEST_FILL_ATTEMPTS = 2
      private const val BREAK_TIMEOUT_TICKS = 200
      private const val MESSAGE_PREFIX = "§7[§bStashMover§7] §f"

      private fun encode(pos: BlockPos): String {
         return pos.x.toString() + "," + pos.y + "," + pos.z
      }

      private fun decode(value: String?): BlockPos? {
         if (value == null) return null
         val coordinates = value.split(",", limit = -1)
         if (coordinates.size != 3) return null
         return try {
            BlockPos(coordinates[0].trim().toInt(),
               coordinates[1].trim().toInt(), coordinates[2].trim().toInt())
         } catch (ignored: NumberFormatException) {
            null
         }
      }

      private fun invIndexToMenuId(menu: ChestMenu, invIndex: Int): Int {
         return menu.rowCount * 9 + (if (invIndex < 9) invIndex + 27 else invIndex - 9)
      }
   }
}
