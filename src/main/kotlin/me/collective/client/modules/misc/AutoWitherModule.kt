package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils

import java.util.ArrayDeque
import java.awt.Color
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Автостройка визера: игрок ставит первый песок душ (прицеливается в клетку
 * центра базового ряда и удерживает «использовать»), модуль достраивает
 * остальные 3 песка и 3 черепа.
 *
 * Порт модуля Auto Wither (CatLean 1.21.11, класс su.catlean.yg): набор настроек
 * range/wall-range/blocks-per-action/delay-per-action/timing/place-mode/switch/
 * interact/pose/rotate/render восстановлен из runtime-расшифровки строк оригинала;
 * Delay/BPT/Rotate/Render взяты из базы PlacementModule. Геометрия постройки —
 * стоячий (классическое T) и лежачий (горизонтальное T) визер.
 */
class AutoWitherModule : PlacementModule("AutoWither", "Ставишь первый песок душ — модуль достраивает визера", ModuleCategory.MISC) {
   private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 4.5f, 2.0f, 6.0f).withUnit("m"))
   private val wallRangeSetting: Setting<Float> = add(NumberSetting("WallRange", 3.0f, 0.0f, 6.0f).withUnit("m"))
   private val timingSetting: Setting<WitherTimingChoice> = add(EnumSetting("Timing", WitherTimingChoice.VANILLA))
   private val placeModeSetting: Setting<WitherPlaceModeChoice> = add(EnumSetting("PlaceMode", WitherPlaceModeChoice.NORMAL))
   private val switchSetting: Setting<WitherSwitchChoice> = add(EnumSetting("Switch", WitherSwitchChoice.NORMAL))
   private val interactSetting: Setting<WitherInteractChoice> = add(EnumSetting("Interact", WitherInteractChoice.CLIENT))
   private val poseSetting: Setting<WitherPoseChoice> = add(EnumSetting("Pose", WitherPoseChoice.STANDING))
   private val schemeSetting: Setting<Boolean> = add(BooleanSetting("Scheme", true).dependsOn(placementRenderSetting))

   private val buildQueue: ArrayDeque<QueueEntry> = ArrayDeque()
   private val actionTimer: ElapsedTimer = ElapsedTimer()
   private var wasBuilding: Boolean = false
   private var stallTicks: Int = 0

   init {
      placementAttackSetting.value = false
      placementJumpSetting.setDefaultValue(false)

      // Перекрываем рендер-конфигурацию базы: цвета Fill/Outline прогоняются через
      // схему модуля Colors (Static/Rainbow/Fade). Регистрация заменяет прежнюю
      // (RenderConfiguration сравнивается по feature).
      ClientContext.featureBoxRenderer.register(
         FeatureBoxRenderer.RenderConfiguration(
            this,
            { schemeTransform(placementFillSetting.value) },
            { schemeTransform(placementOutlineSetting.value) },
            placementLineWidthSetting,
            placementFadeTimeSetting,
            { true },
            placementFadeSetting,
            450
         )
      )
   }

   private fun schemeTransform(base: Color): Color {
      if (!schemeSetting.value || colorsModule == null) {
         return base
      }

      return colorsModule!!.schemeSetting.value.transform(base)
   }

   enum class WitherTimingChoice(private val displayName: String) : NamedEntry {
      VANILLA("Vanilla"),
      INSTANT("Instant");

      override fun getName(): String {
         return displayName
      }
   }

   enum class WitherPlaceModeChoice(private val displayName: String) : NamedEntry {
      NORMAL("Normal"),
      PACKET("Packet");

      override fun getName(): String {
         return displayName
      }
   }

   enum class WitherSwitchChoice(private val displayName: String) : NamedEntry {
      NORMAL("Normal"),
      SILENT("Silent"),
      NONE("None");

      override fun getName(): String {
         return displayName
      }
   }

   enum class WitherInteractChoice(private val displayName: String) : NamedEntry {
      CLIENT("Client"),
      PACKET("Packet");

      override fun getName(): String {
         return displayName
      }
   }

   enum class WitherPoseChoice(private val displayName: String) : NamedEntry {
      STANDING("Standing"),
      LYING("Lying");

      override fun getName(): String {
         return displayName
      }
   }

   private data class QueueEntry(val position: BlockPos, val sand: Boolean)

   override fun onToggle() {
      buildQueue.clear()
      wasBuilding = false
      stallTicks = 0
   }

   override fun onDisable() {
      buildQueue.clear()
      wasBuilding = false
   }

   /**
    * Слот для установки — по типу головы очереди: песок душ или визер-череп.
    * Switch: Normal — только хотбар, Silent — хотбар + инвентарь, None — только то,
    * что уже в руке.
    */
   override fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
      val head = buildQueue.peek()
      if (head == null || isWorldUnavailable()) {
         return InventoryUtils.SwapSlot(-1, false, InteractionHand.MAIN_HAND)
      }

      return findSlotForItem(if (head.sand) Items.SOUL_SAND else Items.WITHER_SKELETON_SKULL)
   }

   private fun findSlotForItem(item: Item): InventoryUtils.SwapSlot {
      val switchMode = switchSetting.value

      if (switchMode == WitherSwitchChoice.NONE) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (player.offhandItem.`is`(item)) {
            return InventoryUtils.SwapSlot(-2, false, InteractionHand.OFF_HAND)
         }

         if (player.mainHandItem.`is`(item)) {
            return InventoryUtils.SwapSlot(InventoryUtils.getSelectedSlot(), true, InteractionHand.MAIN_HAND)
         }

         return InventoryUtils.SwapSlot(-1, false, InteractionHand.MAIN_HAND)
      }

      return InventoryUtils.findSwapSlot(item, switchMode == WitherSwitchChoice.SILENT)
   }

   override fun getPlacementPositions(): MutableList<BlockPos> {
      val positions = ArrayList<BlockPos>()
      if (isWorldUnavailable()) {
         buildQueue.clear()
         return positions
      }

      dropSatisfiedEntries()
      if (buildQueue.isEmpty()) {
         finishOrWaitForTrigger()
      }
      if (buildQueue.isEmpty()) {
         return positions
      }

      // Vanilla-тайминг ставит один блок за действие, Instant — blocks-per-action (BPT базы).
      val vanillaTiming = timingSetting.value == WitherTimingChoice.VANILLA
      val headIsSand = buildQueue.peek()!!.sand

      for (entry in buildQueue) {
         if (entry.sand != headIsSand) {
            continue
         }

         positions.add(entry.position)
         if (vanillaTiming) {
            break
         }
      }

      return positions
   }

   /** Убирает из очереди позиции, у которых блок уже стоит (поставлен нами или игроком). */
   private fun dropSatisfiedEntries() {
      buildQueue.removeIf { entry -> !WorldUtils.isReplaceable(entry.position) }
   }

   private fun finishOrWaitForTrigger() {
      if (wasBuilding) {
         wasBuilding = false
         stallTicks = 0
         printMessage("§aВизер построен.")
         if (placementAutoDisableSetting.value) {
            disable()
         }
         return
      }

      tryBuildQueue()
   }

   /**
    * Триггер как в оригинале: удерживается «использовать», в руке песок душ,
    * прицел наведён на блок — очередь строится от клетки, куда встанет центр ряда.
    */
   private fun tryBuildQueue() {
      val player = ClientAccess.collectiveMinecraft.player ?: return
      if (!ClientAccess.collectiveMinecraft.options.keyUse.isDown) {
         return
      }

      if (InventoryUtils.getHandForItem(Items.SOUL_SAND) == null) {
         return
      }

      val hitResult = ClientAccess.collectiveMinecraft.hitResult
      if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) {
         return
      }

      val target = hitResult.blockPos.relative(hitResult.direction)
      if (!WorldUtils.isReplaceable(target) || !isWithinRange(target, false)) {
         return
      }

      val layout = computeLayout(target)
      for (entry in layout) {
         if (!WorldUtils.isReplaceable(entry.position) || !isWithinRange(entry.position, true)) {
            return
         }
      }

      buildQueue.clear()
      buildQueue.addAll(layout)
      wasBuilding = true
      stallTicks = 0
      actionTimer.reset()
      printMessage("Достраиваю визера: §7осталось §f" + layout.size)
   }

   /**
    * Раскладка от центра базового ряда: ряд песка поперёк взгляда игрока.
    * Standing — классическое T (песок над центром, черепа по краям и сверху);
    * Lying — горизонтальное T (лишний песок сбоку от центра, черепа рядом над базой).
    */
   private fun computeLayout(target: BlockPos): List<QueueEntry> {
      val facing = ClientAccess.collectiveMinecraft.player!!.direction
      val rowAxis = facing.clockWise
      val rowEnd1 = target.relative(rowAxis, -1)
      val rowEnd2 = target.relative(rowAxis, 1)

      val layout = ArrayList<QueueEntry>(7)
      layout.add(QueueEntry(target, true))
      layout.add(QueueEntry(rowEnd1, true))
      layout.add(QueueEntry(rowEnd2, true))

      if (poseSetting.value == WitherPoseChoice.STANDING) {
         layout.add(QueueEntry(target.above(), true))
         layout.add(QueueEntry(rowEnd1.above(), false))
         layout.add(QueueEntry(rowEnd2.above(), false))
         layout.add(QueueEntry(target.above(2), false))
      } else {
         layout.add(QueueEntry(target.relative(facing), true))
         layout.add(QueueEntry(rowEnd1.above(), false))
         layout.add(QueueEntry(target.above(), false))
         layout.add(QueueEntry(rowEnd2.above(), false))
      }

      return layout
   }

   /** Range — до видимой позиции, WallRange — до позиции за стеной. */
   private fun isWithinRange(position: BlockPos, lenient: Boolean): Boolean {
      val center = position.center
      val eye = ClientAccess.collectiveMinecraft.player!!.eyePosition
      val distance = eye.distanceTo(center)

      val limit: Float = if (lenient) {
         // соседние клетки структуры проверяем с запасом на смещение от цели
         maxOf(rangeSetting.value, wallRangeSetting.value) + 1.5f
      } else {
         val builder = BlockRaycastContext.Builder(eye, center)
         // Раньше ветки были инвертированы: цель «за стеной» получала большую дистанцию.
         val blocked = RaycastUtils.raycast(builder.build()).type == HitResult.Type.BLOCK
         if (blocked) wallRangeSetting.value else rangeSetting.value
      }

      return distance <= limit
   }

   override fun placePositions(positions: MutableList<BlockPos>) {
      if (positions.isEmpty() || buildQueue.isEmpty()) {
         return
      }

      // База гейтит цикл своим Delay-таймером; свой таймер отсекает повторную
      // установку в тот же тик (приватный таймер базы сбросить нельзя).
      if (!actionTimer.hasElapsed(placementDelaySetting.value.toLong())) {
         return
      }

      val batchLimit = if (timingSetting.value == WitherTimingChoice.INSTANT) placementBptSetting.value else 1
      val previousSlot = ClientAccess.collectiveMinecraft.player!!.inventory.selectedSlot
      val slot = findPlacementSwapSlot()
      var swapApplied = false
      var placed = 0

      try {
         for (candidateObject in positions) {
            val position = candidateObject as? BlockPos ?: continue

            val entry = findQueueEntry(position) ?: continue

            if (placed >= batchLimit) {
               break
            }

            if (!slot.isFound() || slot.getStack().isEmpty) {
               break
            }

            if (!swapApplied && !slot.isInHand()) {
               InventoryUtils.swapTo(slot)
               swapApplied = true
            }

            if (placeQueueEntry(entry, slot)) {
               buildQueue.remove(entry)
               placed++
            }
         }
      } finally {
         if (swapApplied) {
            InventoryUtils.swapBack(previousSlot, slot)
         }
      }

      if (placed > 0) {
         stallTicks = 0
         actionTimer.reset()
      } else if (buildQueue.isNotEmpty() && ++stallTicks >= STALL_LIMIT_TICKS) {
         stallTicks = 0
         buildQueue.clear()
         wasBuilding = false
         printMessage("§cНе удалось достроить (нет доступа или блоков).")
      }
   }

   private fun findQueueEntry(position: BlockPos): QueueEntry? {
      for (entry in buildQueue) {
         if (entry.position == position) {
            return entry
         }
      }

      return null
   }

   private fun placeQueueEntry(entry: QueueEntry, slot: InventoryUtils.SwapSlot): Boolean {
      val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
      val raytraceEnabled: Boolean = placementRaytraceSetting.value
      val supportDirection = BlockInteractionUtils.findSupportDirection(entry.position, strictDirection, raytraceEnabled)

      val airPlace: Boolean = placementAirPlaceSetting.value
      if (supportDirection == null && !airPlace) {
         return false
      }

      if (supportDirection != null) {
         val rotations = RotationManager.getRotationsToFace(entry.position.center, supportDirection)
         if (placementRotateSetting.value) {
            ClientContext.rotationManager.requestRotation(rotations, this.getPlacementRotationPriority())
         }
      }

      if (interactSetting.value == WitherInteractChoice.PACKET) {
         return placeByPacket(entry, supportDirection)
      }

      val success = BlockInteractionUtils.placeBlockIgnoringCrystals(
         entry.position, null, supportDirection, airPlace, InteractionHand.MAIN_HAND, slot.getStack()
      )
      if (success && placementRenderSetting.value) {
         ClientContext.featureBoxRenderer.addBox(this, entry.position)
      }

      return success
   }

   /** Packet-режим: сырой use-item-on пакет вместо игрового взаимодействия. */
   private fun placeByPacket(entry: QueueEntry, supportDirection: Direction?): Boolean {
      val direction = supportDirection ?: Direction.DOWN
      val neighbor = entry.position.relative(direction)
      val hitLocation = entry.position.center.add(
         Vec3.atLowerCornerOf(direction.unitVec3i).scale(0.5)
      )
      val hitResult = BlockHitResult(hitLocation, direction.opposite, neighbor, false)

      PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hitResult)
      PacketSender.sendSwing(InteractionHand.MAIN_HAND)

      if (placementRenderSetting.value) {
         ClientContext.featureBoxRenderer.addBox(this, entry.position)
      }

      return true
   }

   private fun printMessage(text: String) {
      val message: MutableComponent = Component.literal(MESSAGE_PREFIX + text)
      ChatUtils.printChat(message, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.LOW)
   }

   companion object {
      private const val MESSAGE_PREFIX = "§7[§bAutoWither§7] §f"
      /** Тиков безуспешных попыток, после которых очередь сбрасывается. */
      private const val STALL_LIMIT_TICKS = 60

      private var colorsModule: ColorsModule? = null

      @JvmStatic
      fun bindReferencedModules(moduleRegistry: ModuleRegistry) {
         colorsModule = moduleRegistry.getModule(ColorsModule::class.java) as ColorsModule
      }
   }
}
