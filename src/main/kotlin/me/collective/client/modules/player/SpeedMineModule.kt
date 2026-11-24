package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientLevelAddEntityEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.SelectedSlotChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent
import me.collective.client.managers.ServerStateManager
import me.collective.client.managers.TaskScheduler
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.managers.RotationManager
import me.collective.client.utils.combat.ExtraBreakTarget
import me.collective.client.utils.world.BlockBreakingState
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.inventory.ExtraBreakTracker
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.managers.TickRateController
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.WorldStateManager
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.combat.AnchorAuraModule
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.modules.combat.AutoExpModule
import me.collective.client.modules.combat.AutoMineModule
import me.collective.client.modules.combat.AutoMineMiningTracker
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.render.LogoutSpotsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.settings.options.ListFilterMode
import me.collective.client.settings.options.SpeedMineAutoSwapChoice
import me.collective.client.settings.options.SpeedMineColorModeChoice
import me.collective.client.settings.options.SpeedMineRebreakChoice
import me.collective.client.settings.options.SpeedMineRenderModeChoice
import me.collective.client.settings.options.SpeedMineValue121Choice

import java.awt.Color
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class SpeedMineModule : Module("SpeedMine", "Mines blocks silently.", ModuleCategory.PLAYER) {

   companion object {
      private var autoExpModule: AutoExpModule? = null
      private var autoCrystalModule: AutoCrystalModule? = null
      private var autoMineModule: AutoMineModule? = null
      private var noGlitchBlocksModule: NoGlitchBlocksModule? = null
      private var itemSaverModule: ItemSaverModule? = null
      private var logoutSpotsModule: LogoutSpotsModule? = null
      private var multiTaskModule: MultiTaskModule? = null
      private var anchorAuraModule: AnchorAuraModule? = null

      @JvmField
      var automatedMiningStart: Boolean = false

      private val swapPending = AtomicBoolean()
      private val restartPending = AtomicBoolean()

      @JvmStatic
      fun isSwapPending(): Boolean {
         return swapPending.get()
      }

      @JvmStatic
      fun reset(owner: SpeedMineModule) {
         // Abort the active server-side attempt before clearing the local progress.
         if (owner.miningPos != null) {
            if (!owner.isWorldUnavailable()) {
               val abortAction = Action.ABORT_DESTROY_BLOCK
               val abortPosition = owner.miningPos
               val facePosition = owner.miningPos

               val direction = selectDestroyFace(facePosition!!)
               PacketSender.sendPlayerAction(abortAction, abortPosition!!, direction)
               val swingAction = SwingAction.MINING
               val hand = InteractionHand.MAIN_HAND
               ClientContext.swingManager.swingPacket(hand, swingAction)
               (ClientAccess.collectiveMinecraft.gameMode!! as BlockBreakingState?)!!.setBreakingBlock(false)
               (ClientAccess.collectiveMinecraft.gameMode!! as BlockBreakingState?)!!.setBreakingProgress(0.0F)
            }

         }
         owner.miningPos = null
         owner.miningProgress = 0.0F
         owner.silentSwapProgress = 0.0F
         owner.bestToolSlot = -1
         owner.pendingBreakPositions.clear()

         owner.retargetTimer.reset()
         owner.rebreakTracker.reset()
      }

      @JvmStatic
      fun clearRebreakAndReset(owner: SpeedMineModule) {
         owner.lastMiningPos = null
         reset(owner)
      }

      private fun selectDestroyFace(blockPos: BlockPos): Direction {
         val directions: List<Direction> = BlockInteractionUtils.getStrictDirections(blockPos)
         return if (!directions.isEmpty()) directions[0] else Direction.UP
      }

      @JvmStatic
      fun bindModules(moduleRegistry: ModuleRegistry) {
         autoExpModule = moduleRegistry.getModule(
            AutoExpModule::class.java
         ) as AutoExpModule?
         autoCrystalModule = moduleRegistry.getModule(
            AutoCrystalModule::class.java
         ) as AutoCrystalModule?
         autoMineModule = moduleRegistry.getModule(
            AutoMineModule::class.java
         ) as AutoMineModule?
         noGlitchBlocksModule = moduleRegistry.getModule(
            NoGlitchBlocksModule::class.java
         ) as NoGlitchBlocksModule?
         itemSaverModule = moduleRegistry.getModule(
            ItemSaverModule::class.java
         ) as ItemSaverModule?
         logoutSpotsModule = moduleRegistry.getModule(
            LogoutSpotsModule::class.java
         ) as LogoutSpotsModule?
         multiTaskModule = moduleRegistry.getModule(
            MultiTaskModule::class.java
         ) as MultiTaskModule?
         anchorAuraModule = moduleRegistry.getModule(
            AnchorAuraModule::class.java
         ) as AnchorAuraModule?
      }
   }

   @JvmField
   val blocksSetting: Setting<Set<Block>>
   @JvmField
   val modeSetting: Setting<ListFilterMode>
   @JvmField
   val damageSetting: Setting<Float>
   @JvmField
   val rangeSetting: Setting<Float>
   @JvmField
   val grimSetting: Setting<Boolean>
   private val tpsSyncSetting: Setting<Boolean>
   @JvmField
   val noBreakDelaySetting: Setting<Boolean>
   @JvmField
   val extraBreakSetting: Setting<Boolean>
   @JvmField
   val sneakExtendSetting: Setting<Boolean>
   @JvmField
   val rebreakEnabledSetting: Setting<Boolean>
   @JvmField
   val rebreakSetting: Setting<SpeedMineRebreakChoice>
   @JvmField
   val asyncSetting: Setting<Boolean>
   @JvmField
   val instantDelaySetting: Setting<Float>
   private val holdingBestSetting: Setting<Boolean>
   @JvmField
   val autoSwapSetting: Setting<Boolean>
   @JvmField
   val autoSwapModeSetting: Setting<SpeedMineAutoSwapChoice>
   private val swapBackSetting: Setting<Boolean>
   private val value121Setting: Setting<SpeedMineValue121Choice>
   @JvmField
   val alternativeSetting: Setting<Boolean>
   @JvmField
   val swapResetSetting: Setting<Boolean>
   @JvmField
   val rotateSetting: Setting<Boolean>
   private val limitSetting: Setting<Float>
   @JvmField
   val renderSetting: Setting<Boolean>
   @JvmField
   val lineWidthSetting: Setting<Float>
   @JvmField
   val renderModeSetting: Setting<SpeedMineRenderModeChoice>
   @JvmField
   val airSetting: Setting<Boolean>
   @JvmField
   val fadeSetting: Setting<Boolean>
   @JvmField
   val colorModeSetting: Setting<SpeedMineColorModeChoice>
   @JvmField
   val fillSetting: Setting<Color>
   @JvmField
   val outlineSetting: Setting<Color>
   private val abortSetting: Setting<Boolean>
   private val rebreakTracker: SpeedMineRebreakTracker
   private val renderQueue: SpeedMineRenderQueue
   val extraBreakTracker: ExtraBreakTracker
   private val pendingBreakPositions: ArrayDeque<Boolean>
   private val startDestroyRetryTimer: ElapsedTimer
   private val retargetTimer: ElapsedTimer
   @JvmField
   val breakDelayTimer: ElapsedTimer
   private val itemSpawnTimer: ElapsedTimer
   private val crystalDelayTimer: ElapsedTimer
   private val extraBreakTimer: ElapsedTimer
   private val stalledBreakTimer: ElapsedTimer
   private var waitingForRebreak: Boolean = false
   private var silentSwapProgress: Float = 0.0F
   private var bestToolSlot: Int = 0
   private var previousSelectedSlot: Int = 0
   @JvmField
   var miningProgress: Float = 0.0F
   @Volatile
   var miningPos: BlockPos? = null
      private set
   var lastMiningPos: BlockPos? = null
      private set
   var extraBreakTarget: ExtraBreakTarget? = null
      private set

   @JvmField
   @Volatile
   var crystalAttackPending: Boolean = false

   val rebreakPos: BlockPos?
      get() {
         val rebreakMode: SpeedMineRebreakChoice = this.rebreakSetting.value
         return when (rebreakMode) {
            SpeedMineRebreakChoice.NONE -> null
            SpeedMineRebreakChoice.FAST -> this.lastMiningPos
            SpeedMineRebreakChoice.INSTANT -> this.miningPos
         }
      }

   init {
      this.blocksSetting = this.add(RegistrySetSetting<Block>("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST))
      this.modeSetting = this.add(EnumSetting<ListFilterMode>("Mode", ListFilterMode.ANY))
      this.damageSetting = this.add(NumberSetting<Float>("Damage", 1.0F, 0.6F, 1.0F))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 5.2F, 1.0F, 6.0F).withUnit("m"))
      this.grimSetting = this.add(BooleanSetting("Grim", false))
      this.tpsSyncSetting = this.add(BooleanSetting("TPSSync", true))
      this.noBreakDelaySetting = this.add(BooleanSetting("NoBreakDelay", true))
      this.extraBreakSetting = this.add(BooleanSetting("ExtraBreak", false).markVisibilityDependency())
      this.sneakExtendSetting = this.add(BooleanSetting("SneakExtend", false).dependsOn(this.extraBreakSetting))
      this.rebreakEnabledSetting = this.add(BooleanSetting("Rebreak", true).markAsGroup().markVisibilityDependency())
      this.rebreakSetting = this.add(EnumSetting<SpeedMineRebreakChoice>("Rebreak", SpeedMineRebreakChoice.NONE, this::isRebreakPageExpanded))
      this.asyncSetting = this.add(BooleanSetting("Async", false, this::isInstantRebreak).dependsOn(this.rebreakEnabledSetting))
      this.instantDelaySetting = this.add(NumberSetting<Float>("InstantDelay", 0.0F, 0.0F, 2.0F, this::isInstantRebreakPageExpanded).withUnit("s"))
      this.holdingBestSetting = this.add(BooleanSetting("HoldingBest", false, this::isInstantRebreakPageExpanded))
      this.autoSwapSetting = this.add(BooleanSetting("AutoSwap", true).markAsGroup().markVisibilityDependency())
      this.autoSwapModeSetting = this.add(EnumSetting<SpeedMineAutoSwapChoice>("AutoSwap", SpeedMineAutoSwapChoice.NONE, this::isAutoSwapPageExpanded))
      this.swapBackSetting = this.add(BooleanSetting("SwapBack", false, this::isNormalSwap).dependsOn(this.autoSwapSetting))
      this.value121Setting = this.add(EnumSetting<SpeedMineValue121Choice>("1.21", SpeedMineValue121Choice.OFF, this::isSilentSwapPageExpanded))
      this.alternativeSetting = this.add(BooleanSetting("Alternative", false, this::isSilentSwapPageExpanded))
      this.swapResetSetting = this.add(BooleanSetting("SwapReset", true, this::isSwapResetVisible))
      this.rotateSetting = this.add(BooleanSetting("Rotate", false).markVisibilityDependency())
      this.limitSetting = this.add(NumberSetting<Float>("Limit", 0.9F, 0.0F, 1.0F, this::isRotatePageExpanded)
         .setBoundaryLabel("Off", SettingLabelCondition.MAX))
      this.renderSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.0F, 5.0F, this::isRenderPageExpanded))
      this.renderModeSetting = this.add(EnumSetting<SpeedMineRenderModeChoice>("RenderMode", SpeedMineRenderModeChoice.OUT, this::isRenderPageExpanded))
      this.airSetting = this.add(BooleanSetting("Air", false, this::isAirVisible))
      this.fadeSetting = this.add(BooleanSetting("Fade", false, this::isRenderPageExpanded))
      this.colorModeSetting = this.add(EnumSetting<SpeedMineColorModeChoice>("ColorMode", SpeedMineColorModeChoice.PROGRESS, this::isRenderPageExpanded))
      this.fillSetting = this.add(ColorSetting("Fill", Color(280861183, true), this::isCustomColorVisible))
      this.outlineSetting = this.add(ColorSetting("Outline", Color(-1044538881, true), this::isCustomColorVisible))
      this.abortSetting = this.add(BooleanSetting("Abort", false) { ignored -> false })
      this.rebreakTracker = SpeedMineRebreakTracker(this)
      this.renderQueue = SpeedMineRenderQueue()
      this.extraBreakTracker = ExtraBreakTracker()
      this.pendingBreakPositions = ArrayDeque()
      this.startDestroyRetryTimer = ElapsedTimer()
      this.retargetTimer = ElapsedTimer()
      this.breakDelayTimer = ElapsedTimer()
      this.itemSpawnTimer = ElapsedTimer()
      this.crystalDelayTimer = ElapsedTimer()
      this.extraBreakTimer = ElapsedTimer()
      this.stalledBreakTimer = ElapsedTimer()
      this.bestToolSlot = -1
      this.extraBreakTarget = null
      this.crystalAttackPending = false
      this.blocksSetting.addVisibilityPredicate { ignored -> this.modeSetting.value.isFiltering() }
      this.rebreakEnabledSetting.setConfigName("RebreakPage")
      this.autoSwapSetting.setConfigName("SwapPage")
      this.abortSetting.setChangeCallback {
         if (!this.isWorldUnavailable() && this.abortSetting.value) {
            reset(this)
            this.abortSetting.value = false
         }
      }
   }

   fun isInMiningRange(blockPos: BlockPos): Boolean {
      return SpeedMineRange.isInRange(blockPos, this.rangeSetting.value)
   }

   private fun rotateToBlock(blockPos: BlockPos) {
      val rotationEnabled: Boolean = this.rotateSetting.value
      if (!rotationEnabled) {
         return
      }
      if (RotationManager.isSilentRotationMode()) {
         return
      }
      if (!WorldUtils.canMineBlock(blockPos)) {
         return
      }
      val sampleMode: BlockSampleMode = BlockSampleMode.X8
      var targetPoint: Vec3? = WorldUtils.findVisiblePoint(
         blockPos, sampleMode
      )
      if (targetPoint == null) {
         targetPoint = blockPos.center
      }

      val rotations: FloatArray = RotationManager.getRotationsToPosition(targetPoint)
      val rotationManager: RotationManager = ClientContext.rotationManager
      rotationManager.requestRotation(rotations, 150, true)

   }

   fun getMiningToolSlot(): Int {
      if (this.bestToolSlot < 0) {
         return ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
      } else {
         return this.bestToolSlot
      }
   }

   private fun canBreakNearCrystal(): Boolean {
      val miningPosition = this.miningPos

      if (WorldUtils.getBlock(miningPosition!!) !==
         Blocks.OBSIDIAN) {
         val currentPosition = this.miningPos
         if (!WorldUtils.isAir(currentPosition!!)) {
            return true
         }
      }

      val currentMiningPos = this.miningPos ?: return false
      val miningBounds = AABB(currentMiningPos)
      val entities = ClientAccess.collectiveMinecraft.level!!.entitiesForRendering().iterator()

      while (entities.hasNext()) {
         val entity = entities.next() as Entity
         if (entity is EndCrystal) {
            val crystalBounds = entity.boundingBox
            val verticalInset: Double = MathConstants.ONE_QUARTER
            if (crystalBounds.inflate(0.0, verticalInset, 0.0).intersects(miningBounds)) {
               return true
            }
         }
      }

      val positionAboveCheck = this.miningPos

      if (this.isPlayerAboveBlock(positionAboveCheck)) {
         val crystalDelay: ElapsedTimer = this.crystalDelayTimer

         val baseDelayMillis = 1e+03F
         val serverState: ServerStateManager = ClientContext.serverStateManager

         val scaledDelayMillis: Long = (baseDelayMillis / serverState.getTickRateScale()).toLong()
         if (!crystalDelay.hasElapsed(scaledDelayMillis)) {
            return false
         }
      }

      return true
   }

   fun isBlockAllowed(blockPos: BlockPos): Boolean {
      if (WorldUtils.canMineBlock(blockPos)) {
         val filterMode: ListFilterMode = this.modeSetting.value
         val block: Block = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos).block
         val selectedBlocks: Setting<Set<Block>> = this.blocksSetting
         if (ListFilterMode.matches(filterMode, block, selectedBlocks)) {
            return true
         }
      }

      return false
   }

   private fun isPlayerAboveBlock(blockPos: BlockPos?): Boolean {
      if (blockPos == null) {
         return true
      }

      val blockBounds = AABB(blockPos)
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val players = ArrayList<Any>(level.players() as Collection<Any>)
      if (logoutSpotsModule!!.isToggled()) {
         val logoutPlayers: Collection<Any> = logoutSpotsModule!!.getSpotPlayers() as Collection<Any>
         players.addAll(logoutPlayers)
      }

      val playerIterator = players.iterator()

      while (playerIterator.hasNext()) {
         val player = playerIterator.next() as LivingEntity
         if (player !== ClientAccess.collectiveMinecraft.player!!) {
            var playerBounds = player.boundingBox

            playerBounds = playerBounds.expandTowards(0.0, 1.0, 0.0)

            if (playerBounds.inflate(1.0, 0.0, 1.0).intersects(blockBounds)) {
               if (this.miningPos!!.y - player.blockY > 0) {
                  return true
               }
            }
         }
      }

      return false
   }

   val tickBreakProgress: Double @JvmName("tickBreakProgressProp") get() = getTickBreakProgress()
   val miningOrNearlyBrokenPos: BlockPos? @JvmName("miningOrNearlyBrokenPosProp") get() = getMiningOrNearlyBrokenPos()
   val renderProgress: Float @JvmName("renderProgressProp") get() = getRenderProgress()

   fun getMiningOrNearlyBrokenPos(): BlockPos? {
      if (this.isToggled()) {
         return this.miningPos
      }

      if (ClientAccess.collectiveMinecraft.gameMode!!.isDestroying()) {
         if ((((ClientAccess.collectiveMinecraft.gameMode!! as BlockBreakingState?)!!).getBreakingProgress()).toDouble() >= 0.9) {
            return ((ClientAccess.collectiveMinecraft.gameMode!! as BlockBreakingState?)!!).getCurrentBreakingBlock()
         }
      }

      return null
   }

   fun getMiningProgress(): Float {
      return Mth.clamp(this.miningProgress, 0.0F, 1.0F)
   }

   private fun playBreakEffects(blockPos: BlockPos) {
      if (ClientAccess.collectiveMinecraft.level!!.getFluidState(blockPos).isEmpty()) {
         val state: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
         if (!state.isAir) {
            val soundType: SoundType = state.soundType
            val sound: SoundEvent = soundType.breakSound
            var soundValue: Float = soundType.volume

            soundValue += 1.0F

            val volume: Float = soundValue / 2.0F
            soundValue = soundType.pitch
            val pitch: Float = soundValue * 0.8F
            val soundSource = SoundSource.BLOCKS
            ClientAccess.collectiveMinecraft.level!!.playLocalSound(blockPos, sound, soundSource, volume, pitch, false)
         }

         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val destroyedState: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
         level.addDestroyBlockEffect(blockPos, destroyedState)
      }
   }

   fun getLineWidth(): Float {
      return this.lineWidthSetting.value
   }

   private fun isMainHandMultitask(): Boolean {
      val hand = ClientAccess.collectiveMinecraft.player!!.getUsedItemHand()
      return multiTaskModule!!.shouldPauseModule(this) && hand == InteractionHand.MAIN_HAND
   }

   fun getBreakDelayTimer(): ElapsedTimer {
      return this.breakDelayTimer
   }

   fun getTickBreakProgress(): Double {
      if (this.miningPos == null) {
         return 0.0
      }

      val miningPosition = this.miningPos
      val miningBlockState: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(miningPosition!!)

      val tickRateScale: Float = ClientContext.serverStateManager.getTickRateScale()
      val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory

      val toolSlot: Int = this.getMiningToolSlot()
      val tool: ItemStack = inventory.getItem(toolSlot)
      val onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
      return WorldUtils.getBreakProgress(tool, miningBlockState, onGround) * tickRateScale
   }

   private fun restartForRebreak(): Boolean {
      val rebreakMode: SpeedMineRebreakChoice = this.rebreakSetting.value
      if (!rebreakMode.isInstant()) {
         return false
      }

      if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NONE) {
         return false
      }

      var tracker: SpeedMineRebreakTracker = this.rebreakTracker

      tracker.validateTarget()
      tracker = this.rebreakTracker

      val delayElapsed = tracker.hasRebreakDelayElapsed()
      val targetPosition = this.miningPos
      if (delayElapsed) {
         this.renderQueue.remove(targetPosition!!)

         reset(this)
         restartPending.set(true)
         val scheduleRebreakRotationReceiver: TaskScheduler = ClientContext.taskScheduler
         val scheduleRebreakRotationTask = Runnable {
               restartPending.set(false)
               val direction = Direction.UP
               ClientAccess.collectiveMinecraft.gameMode!!.startDestroyBlock(targetPosition, direction)
            }
         scheduleRebreakRotationReceiver.scheduleRotationTask(scheduleRebreakRotationTask, 1)
      }

      return delayElapsed
   }

   fun isBreakDelayActive(): Boolean {
      if (this.noBreakDelaySetting.value) {
         return false
      }

      val timer: ElapsedTimer = this.breakDelayTimer

      return !timer.hasElapsed(250L)
   }

   private fun isAlternativeAllowed(): Boolean {
      if (RotationManager.is2b2tMode()) {
         val multiTask: MultiTaskModule = multiTaskModule!!

         if (!multiTask.isItemUseRestricted()) {
            val recentMultiTask: MultiTaskModule = multiTaskModule!!

            if (!recentMultiTask.isItemUseActiveOrRecent(150L)) {
               return false
            }
         }
      }

      return this.alternativeSetting.value
   }

   fun getRenderProgress(): Float {
      val damage = this.damageSetting.value
      return Mth.clamp(this.getMiningProgress() / damage, 0.0F, 1.0F)
   }

   override fun getInfo(): String? {
      val miningPosition = this.miningPos

      if (!WorldUtils.canMineBlock(miningPosition)) {
         return null
      }

      val progress = this.getRenderProgress()
      return "%.2f".format(progress)
   }

   override fun onEnable() {
      reset(this)
      this.lastMiningPos = null
      swapPending.set(false)

      this.renderQueue.clear()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val tpsSyncEnabled: Boolean = this.tpsSyncSetting.value
      val tickScale: Float
      if ((tpsSyncEnabled)) {
         tickScale = ClientContext.serverStateManager.getTickRateScale()
      } else {
         tickScale = 1.0F
      }

      var progressScale = tickScale

      val multiplier = 1.0F
      val tickRateController: TickRateController = ClientContext.tickRateController
      progressScale *= multiplier / tickRateController.getMultiplier()

      this.extraBreakTracker.pruneExpired()

      this.renderQueue.snapshotProgress()
      this.updateExtraBreakProgress(progressScale)
      this.updateMiningProgress(progressScale)
   }

   private fun updateExtraBreakProgress(progressScale: Float) {
      if (this.extraBreakTarget != null) {
         val extraPosition: BlockPos = this.extraBreakTarget!!.position
         val extraState: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(extraPosition)
         val alternativeEnabled: Boolean = this.alternativeSetting.value
         val allowInventoryTools: Boolean = !alternativeEnabled
         val extraToolSlot: Int = InventoryUtils.findBestToolSlot(
            extraPosition, allowInventoryTools)
         val extraTool: ItemStack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(extraToolSlot)
         val extraOnGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
         val extraProgress: Double = WorldUtils.getBreakProgress(
               extraTool, extraState, extraOnGround
            ) *
         progressScale
         val extraTarget: ExtraBreakTarget = this.extraBreakTarget!!
         val extraProgressFloat: Float = extraProgress.toFloat()

         extraTarget.addProgress(extraProgressFloat)
         if (!(extraPosition == this.miningPos)) {
            val extraRenderQueue: SpeedMineRenderQueue = this.renderQueue

            val extraRenderProgress: Float = this.extraBreakTarget!!.clampedProgress
            extraRenderQueue.setProgress(extraPosition, extraRenderProgress)
         }

         if (ClientAccess.collectiveMinecraft.level!!.isEmptyBlock(extraPosition)) {
            this.extraBreakTarget = null

            this.extraBreakTimer.reset()
         }
      }
   }

   private fun updateMiningProgress(progressScale: Float) {
      if (this.lastMiningPos != null) {
         val lastPosition = this.lastMiningPos
         if ((ClientAccess.collectiveMinecraft.level!!.getBlockState(lastPosition!!).block is ShulkerBoxBlock)) {
            val worldState: WorldStateManager = ClientContext.worldStateManager
            val placedPosition = this.lastMiningPos

            if ((worldState.hasKnownPlacement(placedPosition!!))) {
               this.lastMiningPos = null
            }
         }
      }

      val pendingRestart = restartPending
      if (!(pendingRestart.get())) {
         if (this.miningPos == null) {
            if (this.lastMiningPos != null) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               if (!(player.isCreative())) {
                  val rebreakPosition = this.lastMiningPos

                  if ((WorldUtils.canMineBlock(rebreakPosition))) {
                     if ((this.rebreakSetting.value).isFast()) {
                        this.miningPos = this.lastMiningPos
                     }
                  }
               }
            }
         } else {
            val currentPosition = this.miningPos

            if (!(WorldUtils.canMineBlock(currentPosition))) {
               if ((this.rebreakSetting.value).isInstant()) {
                  val airPosition = this.miningPos
                  if ((WorldUtils.isAir(airPosition!!))) {
                     if (this.airSetting.value) {
                        val airRenderQueue: SpeedMineRenderQueue = this.renderQueue
                        val airRenderPosition = this.miningPos
                        val airProgress = this.miningProgress
                        val airDamageThreshold: Float? = this.damageSetting.value
                        val airRenderProgress = airProgress / airDamageThreshold!!
                        airRenderQueue.setProgress(airRenderPosition!!, airRenderProgress)
                     }
                  }
               }

               if ((this.rebreakSetting.value).isFast()) {
                  val pendingPositions: ArrayDeque<Boolean> = this.pendingBreakPositions
                  val queuedOnGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
                  val queuedGroundState: Boolean = queuedOnGround
                  pendingPositions.add(queuedGroundState)

                  if (this.pendingBreakPositions.size > (200)) {
                     this.pendingBreakPositions.removeLast()
                  }
               }
            } else {
               val miningPosition = this.miningPos
               val miningState: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(miningPosition!!)
               val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory

               val toolSlot: Int = this.getMiningToolSlot()
               var toolStack: ItemStack = inventory.getItem(toolSlot)
               val unenchantedStack = ItemStack(toolStack.item)

               val selectedToolSlot: Int = this.getMiningToolSlot()
               val selectionInventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
               if (selectedToolSlot != selectionInventory.getSelectedSlot()) {
                  if (this.value121Setting.value == SpeedMineValue121Choice.SLOW) {
                     toolStack = unenchantedStack
                  }
               }

               // Fast rebreak accumulates ticks while the old block is absent and replays them on return.
               var processedQueuedProgress: Boolean = false
               if ((this.rebreakSetting.value).isFast()) {
                  while (true) {
                     if ((this.pendingBreakPositions.isEmpty())) {
                        break
                     }

                     val queuedOnGround: Boolean = this.pendingBreakPositions.poll()!!
                     this.miningProgress = this.miningProgress +
         (WorldUtils.getBreakProgress(
                              toolStack, miningState, queuedOnGround
                           ) *
         progressScale).toFloat()
                     processedQueuedProgress = true
                     val queuedRenderQueue: SpeedMineRenderQueue = this.renderQueue
                     val queuedPosition = this.miningPos
                     val queuedProgress = this.miningProgress
                     val queuedDamageThreshold: Float? = this.damageSetting.value
                     val queuedRenderProgress = queuedProgress / queuedDamageThreshold!!
                     queuedRenderQueue.setProgressTwice(queuedPosition!!, queuedRenderProgress)
                  }
               }

               if (processedQueuedProgress) {
                  val obsidian: Block = Blocks.OBSIDIAN
                  if ((miningState.`is`(obsidian))) {
                     val obsidianProgress = this.miningProgress

                     val maximumObsidianProgress = 0.99F
                     this.miningProgress = Mth.clamp(
                        obsidianProgress, 0.0F, maximumObsidianProgress
                     )
                     val obsidianRenderQueue: SpeedMineRenderQueue = this.renderQueue
                     val obsidianPosition = this.miningPos
                     val clampedObsidianProgress = this.miningProgress
                     val obsidianDamageThreshold: Float? = this.damageSetting.value
                     val obsidianRenderProgress = clampedObsidianProgress / obsidianDamageThreshold!!
                     obsidianRenderQueue.setProgressTwice(obsidianPosition!!, obsidianRenderProgress)
                     return
                  }
               }

               // Track the unenchanted tool separately for the delayed silent-swap decision.
               val previousSilentProgress = this.silentSwapProgress
               val silentOnGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
               this.silentSwapProgress = previousSilentProgress +
         (WorldUtils.getBreakProgress(
                        unenchantedStack, miningState, silentOnGround
                     ) *
         progressScale).toFloat()
               val previousMiningProgress = this.miningProgress
               val miningOnGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
               this.miningProgress = previousMiningProgress +
         (WorldUtils.getBreakProgress(
                        toolStack, miningState, miningOnGround
                     ) *
         progressScale).toFloat()
               val miningRenderQueue: SpeedMineRenderQueue = this.renderQueue
               val renderPosition = this.miningPos
               val renderProgress = this.miningProgress
               val renderDamageThreshold: Float? = this.damageSetting.value
               val renderFraction = renderProgress / renderDamageThreshold!!
               miningRenderQueue.setProgress(renderPosition!!, renderFraction)
            }
         }
      }
   }

   private fun canReplaceExtraBreakTarget(candidate: ExtraBreakTarget?, blockPos: BlockPos): Boolean {
      val timer: ElapsedTimer = this.extraBreakTimer

      if (!timer.hasElapsed(200L)) {
         return false
      }

      if (candidate == null) {
         return true
      }

      val samePosition = candidate.position.equals(blockPos)

      val candidatePosition = candidate.position
      val invalidCandidate: Boolean = !this.isBlockAllowed(candidatePosition)
      // Невалидный старый таргет можно заменять всегда — иначе extra-break залипал
      // на недоступном блоке и блокировал sneak-extend.
      if (invalidCandidate) {
         return true
      }
      if (!samePosition && !swapPending.get()) {
         return true
      }

      return false
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      reset(this)
      this.lastMiningPos = null
      this.extraBreakTarget = null
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      // PacketEvent.Receive постится из netty-потока (ConnectionMixin.channelRead0):
      // все мутации игрового состояния переносим на клиентский поток, иначе гонки
      // с тик-циклом (очереди, позиции, render-состояние).
      ClientAccess.collectiveMinecraft.execute {
         if (event.packet is ClientboundRespawnPacket) {
            reset(this)
            this.lastMiningPos = null
            this.extraBreakTarget = null
         }

         this.rebreakTracker.handleBlockUpdates(event)
      }
   }

   fun hasCrystalDelayElapsed(delayMillis: Long): Boolean {
      return this.crystalDelayTimer.hasElapsed(delayMillis)
   }

   @EventHandler(priority = 100)
   private fun onSelectedSlotChange(event: SelectedSlotChangeEvent) {
      val alternativeEnabled: Boolean = this.alternativeSetting.value
      if (!alternativeEnabled) {
         return
      }
      if (this.autoSwapModeSetting.value != SpeedMineAutoSwapChoice.SILENT) {
         return
      }
      val resetEnabled: Boolean = this.swapResetSetting.value
      if (!resetEnabled) {
         return
      }
      // A selected-slot change restarts the attempt after rotation scheduling has caught up.
      restartPending.set(true)
      val currentPosition = this.miningPos

      if (WorldUtils.canMineBlock(currentPosition)) {
         val renderPosition = this.miningPos
         this.renderQueue.setProgressTwice(renderPosition!!, 0.0F)
      }

      this.silentSwapProgress = 0.0F
      this.miningProgress = 0.0F
      val scheduleStartDestroyRotationReceiver: TaskScheduler = ClientContext.taskScheduler
      val scheduleStartDestroyRotationTask = Runnable {
            restartPending.set(false)
            val targetPosition = this.miningPos
            if (targetPosition != null) {
               val swingAction = SwingAction.MINING
               val hand = InteractionHand.MAIN_HAND
               ClientContext.swingManager.swing(hand, swingAction)

               if (WorldUtils.canMineBlock(targetPosition)) {
                  automatedMiningStart = true

                  reset(this)

                  val startEvent = MultiPlayerGameModeStartDestroyBlockEvent(
                     targetPosition,
                     selectDestroyFace(targetPosition)
                  )
                  this.onMultiPlayerGameModeStartDestroyBlockEvent(startEvent)
                  automatedMiningStart = false
               } else {
                  if ((this.rebreakSetting.value).isFast()) {
                     val startAction = Action.START_DESTROY_BLOCK

                     val direction = selectDestroyFace(targetPosition)
                     PacketSender.sendPlayerAction(
                        startAction, targetPosition, direction
                     )
                     this.pendingBreakPositions.clear()
                  }
               }
            }
         }
      scheduleStartDestroyRotationReceiver.scheduleRotationTask(scheduleStartDestroyRotationTask, 1)
      return

   }

   @EventHandler
   private fun onClientLevelAddEntityEvent(event: ClientLevelAddEntityEvent) {
      if (this.miningPos != null) {
         if ((this.rebreakSetting.value).isInstant()) {
            if (!(event.entity is ItemEntity)) {
               return
            }

            val currentMiningPos = this.miningPos ?: return
      val miningBounds = AABB(currentMiningPos)
            val entityPosition = event.entity.position()
            if (miningBounds.contains(entityPosition)) {
               if (this.asyncSetting.value) {
                  this.crystalDelayTimer.reset()
               }

               if (this.alternativeSetting.value) {
                  this.itemSpawnTimer.clear()
               }
            }

            return
         }
      }
   }

   @EventHandler
   private fun onRenderWorld(event: WorldRenderEvent.Main) {
      if (this.renderSetting.value) {
         if (ClientAccess.collectiveMinecraft.player!!.gameMode() == GameType.SURVIVAL) {
            this.renderQueue.render(event)
         }
      }
   }

   @EventHandler(priority = -100)
   private fun onMultiPlayerGameModeStartDestroyBlockEvent(
      event: MultiPlayerGameModeStartDestroyBlockEvent
   ) {
      var requestedPosition: BlockPos = event.pos
      if (event.isCancelled()) {
         return
      }
      if (!this.isBlockAllowed(requestedPosition)) {
         this.lastMiningPos = null

         reset(this)

         if (!WorldUtils.canMineBlock(requestedPosition)) {
            event.cancel()
         }
      } else {
         val spectatorCheckPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (!spectatorCheckPlayer.isSpectator()) {
            val creativeCheckPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            if (!creativeCheckPlayer.isCreative()) {
               event.cancel()
               var timer: ElapsedTimer = this.startDestroyRetryTimer

               if (!timer.hasElapsed(100L) &&
         !automatedMiningStart) {
                  if (requestedPosition.equals(this.miningPos)) {
                     timer = this.startDestroyRetryTimer

                     timer.reset()
                  }

                  return
               }

               if (this.extraBreakSetting.value) {
                  if (this.sneakExtendSetting.value) {
                     val sneakKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyShift
                     if (KeyUtils.isPhysicallyDown(sneakKey)) {
                        val currentExtraTarget = this.extraBreakTarget
                        if (!this.canReplaceExtraBreakTarget(currentExtraTarget, requestedPosition)) {
                           return
                        }

                        if (this.extraBreakTarget == null) {
                           val rotations: FloatArray = RotationManager.getRotationsToBlock(
                              requestedPosition)
                           val yaw = rotations[0]
                           val pitch = rotations[1]
                           val blockCenter = requestedPosition.center
                           val lookDirection = Vec3.directionFromRotation(pitch, yaw)
                           val containingPosition: Position = blockCenter.add(lookDirection)

                           val extendedPosition = BlockPos.containing(containingPosition)
                           if (this.isBlockAllowed(extendedPosition)) {
                              requestedPosition = extendedPosition
                           }
                        }
                     }
                  }
               }

               if (!WorldUtils.canMineBlock(requestedPosition) &&
         !automatedMiningStart) {
                  return
               }

               if ((this.extraBreakSetting.value) && this.extraBreakTarget != null) {
                  val existingExtraTarget: ExtraBreakTarget = this.extraBreakTarget!!

                  if (requestedPosition.equals(existingExtraTarget.position)) {
                     return
                  }
               }

               if (this.isBreakDelayActive()) {
                  val currentTarget = this.miningPos

                  if (!WorldUtils.canMineBlock(currentTarget)) {
                     reset(this)
                  }

                  return
               }
               if (!this.isInMiningRange(requestedPosition)) {
                  return
               }

               timer = this.startDestroyRetryTimer

               timer.reset()
               var changedTarget = false
               if (this.miningPos != null &&
         !this.miningPos!!.equals(requestedPosition)) {
                  val oldRenderQueue: SpeedMineRenderQueue = this.renderQueue
                  val oldTarget = this.miningPos
                  oldRenderQueue.remove(oldTarget!!)

                  reset(this)
                  changedTarget = true
               }

               if (!changedTarget &&
         this.miningPos != null &&
         this.miningPos!!.equals(requestedPosition)) {
                  timer = this.retargetTimer

                  if (timer.hasElapsed(100L)) {
                     val retargetRenderQueue: SpeedMineRenderQueue = this.renderQueue
                     val retargetPosition = this.miningPos
                     retargetRenderQueue.remove(retargetPosition!!)
                     this.lastMiningPos = null

                     reset(this)
                  }

                  return
               }

               if (this.alternativeSetting.value) {
                  if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT) {
                     if (this.swapResetSetting.value) {
                        if (this.crystalAttackPending) {
                           this.crystalAttackPending = false

                           reset(this)
                           return
                        }

                        if (autoExpModule!!.isToggled()) {
                           if (autoExpModule!!.modeSetting.value == AutoExpModule.ModeChoice.SILENT &&
         !autoExpModule!!.stopped
                              )
                            {
                              reset(this)
                              return
                           }
                        }
                     }
                  }
               }

               if (!automatedMiningStart) {
                  autoMineModule!!.setMiningActive(true)
               }

               this.lastMiningPos = requestedPosition
               this.miningPos = requestedPosition
               val facePosition = this.miningPos

               val destroyFace: Direction = selectDestroyFace(facePosition!!)
               val miningSwing = SwingAction.MINING
               val miningHand = InteractionHand.MAIN_HAND
               ClientContext.swingManager.swing(miningHand, miningSwing)
               val instantBreakPosition = this.miningPos

               val instantBreak: InstantBreakResult = this.evaluateInstantBreak(instantBreakPosition!!)
               var toolSlot: Int = instantBreak.toolSlot
               val selectedSlot: Int = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
               val hardnessPosition = this.miningPos

               val hardness: Float = WorldUtils.getHardness(hardnessPosition!!)
               if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NORMAL) {
                  val toolSearchPosition = this.miningPos

                  // NORMAL только переключает хотбар-слот: поиск по всему
                  // инвентарю даёт слот 9..35, который selectHotbarSlot
                  // молча игнорирует (инструмент не берётся вообще).
                  toolSlot = InventoryUtils.findBestToolSlot(
                     toolSearchPosition!!, false)
                  if (toolSlot != selectedSlot) {
                     if (this.swapBackSetting.value) {
                        this.previousSelectedSlot = selectedSlot
                     }
                  }
               }

               if (instantBreak.canInstantBreak) {
                  if (this.alternativeSetting.value &&
         this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT) {
                     InventoryUtils.swapIntoSelectedSlot(toolSlot)
                  } else if (selectedSlot != instantBreak.toolSlot) {
                     InventoryUtils.selectHotbarSlotForPlacement(toolSlot)
                  }
               }

               // Extra break sends a start and stop now so its progress can be tracked separately.
               if (this.extraBreakSetting.value && !instantBreak.breaksInOneTick) {
                  val extraStartPosition = this.miningPos
                  val extraStartAction = Action.START_DESTROY_BLOCK
                  PacketSender.sendPlayerAction(extraStartAction, extraStartPosition!!, destroyFace)
                  val extraGrimPosition = this.miningPos
                  this.sendGrimStartPacket(extraGrimPosition!!, destroyFace)
                  val extraStopPosition = this.miningPos
                  val extraStopAction = Action.STOP_DESTROY_BLOCK
                  PacketSender.sendPlayerAction(extraStopAction, extraStopPosition!!, destroyFace)
                  timer = this.breakDelayTimer
                  timer.reset()
                  if (this.extraBreakTarget == null) {
                     this.extraBreakTarget = ExtraBreakTarget(this.miningPos!!)
                  }
               } else {
                  val startPosition = this.miningPos
                  val startAction = Action.START_DESTROY_BLOCK
                  PacketSender.sendPlayerAction(startAction, startPosition!!, destroyFace)
                  val grimPosition = this.miningPos
                  this.sendGrimStartPacket(grimPosition!!, destroyFace)
                  if (instantBreak.canInstantBreak) {
                     if (!instantBreak.breaksInOneTick) {
                        val stopPosition = this.miningPos
                        val stopAction = Action.STOP_DESTROY_BLOCK
                        PacketSender.sendPlayerAction(
                           stopAction, stopPosition!!, destroyFace
                        )
                        timer = this.breakDelayTimer

                        timer.reset()
                     }
                  }
               }

               if (instantBreak.canInstantBreak && toolSlot != -1) {
                  if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT) {
                     if (this.alternativeSetting.value) {
                        InventoryUtils.swapIntoSelectedSlot(toolSlot)
                     } else if (selectedSlot != toolSlot) {
                        InventoryUtils.selectHotbarSlotForPlacement(selectedSlot)
                     }
                  }

                  val worldState: WorldStateManager = ClientContext.worldStateManager
                  val predictedPosition = this.miningPos

                  worldState.destroyBlockPredicted(predictedPosition)
                  val attemptPosition = this.miningPos
                  this.recordBreakAttempt(attemptPosition!!)
                  this.startDestroyRetryTimer.clear()
                  this.extraBreakTarget = null
                  if ((this.rebreakSetting.value).isInstant() && hardness == 0.0F) {
                     this.waitingForRebreak = true
                  }

                  if ((this.rebreakSetting.value).isNone()) {
                     this.miningPos = null
                  }
               }

               timer = this.retargetTimer

               timer.reset()
               return
            }
         }
      }

   }

   @EventHandler(priority = 100)
   private fun onMiningTick(event: RotationRequestEvent) {
      var extraInventorySwap: Boolean
      var canSwapTools: Boolean
      if ((this.waitingForRebreak)) {
         val rebreakPosition = this.miningPos

         if ((WorldUtils.canMineBlock(rebreakPosition))) {
            val rebreakPositionForHardness = this.miningPos

            if (WorldUtils.getHardness(rebreakPositionForHardness!!) > 0.0F) {
               this.waitingForRebreak = false
               this.miningProgress = 0.0F
            }
         }
      }

      var currentMiningPosition = this.miningPos

      if ((WorldUtils.canMineBlock(currentMiningPosition))) {
         val toolSearchPosition = this.miningPos
         val alternativeEnabled: Boolean = this.alternativeSetting.value
         val allowInventoryTools: Boolean = !alternativeEnabled
         this.bestToolSlot = InventoryUtils.findBestToolSlot(
            toolSearchPosition!!, allowInventoryTools)
      } else {
         if ((this.rebreakSetting.value).isNone()) {
            reset(this)
         }
      }

      val toolSlot: Int = this.bestToolSlot
      if (swapPending.get()) {
         return
      }
      if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NONE) {
         val heldStack = ClientAccess.collectiveMinecraft.player!!.mainHandItem

         if (!(ItemSaverModule.isSafeToUse(heldStack))) {
            if ((itemSaverModule!!.isToggled())) {
               return
            }
         }
      }

      val health: Float = EntityUtils.getPlayerHealthWithAbsorption()
      canSwapTools = true
      if (!(health > 4.0F)) {
         val heldStack = ClientAccess.collectiveMinecraft.player!!.mainHandItem
         val totem: Item = Items.TOTEM_OF_UNDYING
         if (heldStack.`is`(totem)) {
            canSwapTools = false
         }
      }

      if (this.extraBreakTarget != null) {
         val extraTarget: ExtraBreakTarget = this.extraBreakTarget!!

         if (!(extraTarget.position
                  .equals(this.miningPos))) {
            if (this.extraBreakTarget!!.clampedProgress >= 1.0F) {
               if (!(this.isMainHandMultitask()) &&
         canSwapTools) {
                  swapPending.set(true)
                  extraInventorySwap = false
                  if (this.alternativeSetting.value) {
                     if (!RotationManager.is2b2tMode()) {
                        extraInventorySwap = true
                     }
                  }

                  val useExtraInventorySwap = extraInventorySwap
                  val extraPreviousSlot: Int = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()

                  currentMiningPosition = this.extraBreakTarget!!.position
                  val allowExtraInventoryTools: Boolean = !useExtraInventorySwap
                  val extraToolSlot: Int = InventoryUtils.findBestToolSlot(
                     currentMiningPosition!!, allowExtraInventoryTools)
                  if (useExtraInventorySwap) {
                     InventoryUtils.swapIntoSelectedSlot(extraToolSlot)
                  } else {
                     PacketSender.sendSelectedSlot(extraToolSlot)
                  }

                  val extraSwingManager: SwingInteractionManager = ClientContext.swingManager
                  val extraSwingHand = InteractionHand.MAIN_HAND
                  val extraSwingAction = SwingAction.MINING
                  extraSwingManager.swing(
                     extraSwingHand, extraSwingAction, true
                  )

                  val extraRotationPosition = this.extraBreakTarget!!.position

                  this.rotateToBlock(extraRotationPosition)

                  val extraAttemptPosition = this.extraBreakTarget!!.position
                  this.recordBreakAttempt(extraAttemptPosition)

                  val extraEffectPosition = this.extraBreakTarget!!.position

                  this.playBreakEffects(extraEffectPosition)

                  this.extraBreakTimer.reset()

                  ClientContext.worldStateManager.markExtraBreak(this.extraBreakTarget!!.position)
                  val miningTracker = autoMineModule!!.miningTracker

                  val trackedExtraPosition = this.extraBreakTarget!!.position
                  miningTracker.rememberPosition(trackedExtraPosition)
                  this.extraBreakTarget = null
                  val extraSwapScheduler: TaskScheduler = ClientContext.taskScheduler

                  // Restore the same swap mechanism after the extra break has been sent.
                  val restoreExtraSlot = Runnable {
                     if (useExtraInventorySwap) {
                        InventoryUtils.swapIntoSelectedSlot(extraToolSlot)
                     } else {
                        PacketSender.sendSelectedSlot(extraPreviousSlot)
                        val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                        val inventory: Inventory = player.inventory
                        inventory.setSelectedSlot(extraPreviousSlot)
                     }
                     swapPending.set(false)
                  }

                  extraSwapScheduler.scheduleRotationTask(restoreExtraSlot, 2)
                  return
               }
            }
         }
      }

      val lastBreakWasShulker = this.extraBreakTracker.getLastBlock() is ShulkerBoxBlock

      val blockedByContainer = RotationManager.is2b2tMode() &&
         ClientAccess.collectiveMinecraft.screen is AbstractContainerScreen<*>
      currentMiningPosition = this.miningPos

      var canContinueMining = WorldUtils.canMineBlock(currentMiningPosition)
      if (((this.asyncSetting.value)) && !blockedByContainer && !lastBreakWasShulker) {
         canContinueMining = true
      }

      if (this.miningPos == null) {
         canContinueMining = false
      }

      currentMiningPosition = this.miningPos

      if (!(WorldUtils.canMineBlock(currentMiningPosition))) {
         val aboveCheckPosition = this.miningPos

         if ((this.isPlayerAboveBlock(aboveCheckPosition))) {
            canContinueMining = false
         }
      }

      // Normal auto-swap keeps the chosen tool until mining can no longer continue.
      if (this.previousSelectedSlot != -1 && !canContinueMining) {
         if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NORMAL) {
            if (this.swapBackSetting.value) {
               val previousSlot = this.previousSelectedSlot

               InventoryUtils.selectHotbarSlotForPlacement(previousSlot)
               this.previousSelectedSlot = -1
               return
            }
         }
      }

      if (!(this.restartForRebreak())) {
         if (this.miningPos != null) {
            val anchorAura: AnchorAuraModule = anchorAuraModule!!
            val anchorPosition = this.miningPos

            if ((anchorAura.isManagedAnchor(anchorPosition!!))) {
               return
            }
         }

         val spectatorCheckPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (!(spectatorCheckPlayer.isSpectator())) {
            val creativeCheckPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            if (!(creativeCheckPlayer.isCreative()) && canContinueMining) {
               var rotate = true
               if ((this.limitSetting.value) != 1.0F) {
                  val progress = this.getMiningProgress()
                  val damage: Float? = this.damageSetting.value
                  val fraction = progress / damage!!
                  val limit: Float? = this.limitSetting.value
                  if (!(fraction >= limit!!)) {
                     rotate = false
                  } else if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NONE) {
                     if (this.holdingBestSetting.value) {
                        val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
                        if (toolSlot != inventory.getSelectedSlot()) {
                           rotate = false
                        }
                     }
                  }
               }
               if (rotate) {
                  val rotationTarget = this.miningPos
                  this.rotateToBlock(rotationTarget!!)
               }

               val progress = this.getMiningProgress()
               val damageThreshold: Float? = this.damageSetting.value
               if (progress < damageThreshold!!) {
                  return
               }

               val alternativeAllowed = this.isAlternativeAllowed()
               var instantDelay: Float = this.instantDelaySetting.value!!
               if (alternativeAllowed) {
                  if ((RotationManager.is2b2tMode())) {
                     val minimumInstantDelay = 0.1F
                     val configuredInstantDelay = instantDelay
                     instantDelay = Math.max(configuredInstantDelay, minimumInstantDelay)
                  }
               }

               if ((this.rebreakSetting.value).isInstant()) {
                  val instantDelaySeconds: Double = instantDelay.toDouble()
                  val instantDelayUnit: TimeUnit = TimeUnit.SECONDS
                  val instantDelayTimer: ElapsedTimer = this.breakDelayTimer
                  if (!(instantDelayTimer.hasElapsed(instantDelaySeconds, instantDelayUnit))) {
                     return
                  }
               }

               if (!(this.isMainHandMultitask()) ||
         alternativeAllowed) {
                  val holdingTool = toolSlot == InventoryUtils.getSelectedSlot()
                  if (((this.rebreakSetting.value).isInstant()) &&
         !holdingTool &&
         alternativeAllowed) {
                     val itemSpawnDelay: ElapsedTimer = this.itemSpawnTimer

                     if (!(itemSpawnDelay.hasElapsed(150L))) {
                        return
                     }
                  }

                  if (!(this.canBreakNearCrystal())) {
                     return
                  }

                  val selectedSlot: Int = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
                  val statePosition = this.miningPos
                  val miningState: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(statePosition!!)
                  val facePosition = this.miningPos

                  val destroyFace: Direction = selectDestroyFace(facePosition!!)
                  var swappedTool = false
                  if (this.autoSwapModeSetting.value != SpeedMineAutoSwapChoice.NONE && toolSlot != -1) {
                     if (ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot() != toolSlot) {
                        if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT && alternativeAllowed) {
                           InventoryUtils.swapIntoSelectedSlot(
                              toolSlot)
                        } else {
                           this.previousSelectedSlot = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()

                           InventoryUtils.selectHotbarSlotForPlacement(
                              toolSlot)
                        }
                        swappedTool = true
                     }
                  }

                  if (!swappedTool && selectedSlot != toolSlot) {
                     if ((this.rebreakSetting.value).isInstant()) {
                        if (this.holdingBestSetting.value) {
                           return
                        }
                     }
                  }

                  this.breakDelayTimer.reset()

                  this.itemSpawnTimer.reset()
                  val finishSwingAction = SwingAction.MINING
                  val finishSwingHand = InteractionHand.MAIN_HAND
                  ClientContext.swingManager.swingPacket(finishSwingHand, finishSwingAction)
                  currentMiningPosition = this.miningPos

                  var recordAttempt = WorldUtils.canMineBlock(currentMiningPosition)
                  if (!recordAttempt) {
                     val asyncEnabled: Boolean = this.asyncSetting.value
                     recordAttempt = asyncEnabled
                  }

                  if (recordAttempt) {
                     val attemptedPosition = this.miningPos
                     this.recordBreakAttempt(attemptedPosition!!)
                  }

                  currentMiningPosition = this.miningPos

                  if ((WorldUtils.canMineBlock(currentMiningPosition))) {
                     if (this.sneakExtendSetting.value) {
                        if ((ClientAccess.collectiveMinecraft.player!!.isShiftKeyDown())) {
                           val predictedWorldState: WorldStateManager = ClientContext.worldStateManager
                           val predictedPosition = this.miningPos

                           predictedWorldState.destroyBlockPredicted(predictedPosition)
                        }
                     }
                  }

                  if (!(RotationManager.isMovementSyncEnabled())) {
                     val rebreakMode: SpeedMineRebreakChoice = this.rebreakSetting.value
                     if (!(rebreakMode.isInstant())) {
                        val playerX = ClientAccess.collectiveMinecraft.player!!.getX()
                        val playerY = ClientAccess.collectiveMinecraft.player!!.getY()
                        val playerZ = ClientAccess.collectiveMinecraft.player!!.getZ()
                        val packetOnGround = ClientAccess.collectiveMinecraft.player!!.onGround()
                        PacketSender.sendPosition(
                           playerX, playerY, playerZ, packetOnGround
                        )
                     }
                  }

                  val brokenPosition = this.miningPos
                  val tracker: SpeedMineRebreakTracker = this.rebreakTracker
                  val trackedPosition = this.miningPos

                  tracker.startTracking(trackedPosition!!)
                  if ((this.waitingForRebreak)) {
                     val restartPosition = this.miningPos
                     val restartAction = Action.START_DESTROY_BLOCK
                     PacketSender.sendPlayerAction(
                        restartAction, restartPosition!!, destroyFace
                     )
                  }

                  if (!(miningState.isAir)) {
                     if ((this.stalledBreakTimer.hasElapsedAndReset(200L))) {
                        val effectPosition = this.miningPos

                        this.playBreakEffects(effectPosition!!)
                     }
                  }

                  // Record the stop as unconfirmed until a block update arrives or the tracking window expires.
                  val stopPosition = this.miningPos
                  val stopAction = Action.STOP_DESTROY_BLOCK
                  PacketSender.sendPlayerAction(stopAction, stopPosition!!, destroyFace)
                  val unconfirmedWorldState: WorldStateManager = ClientContext.worldStateManager
                  val unconfirmedPosition = this.miningPos

                  unconfirmedWorldState.markUnconfirmedBreak(unconfirmedPosition!!)
                  if ((this.rebreakSetting.value).isFast()) {
                     val abortPosition = this.miningPos
                     val abortAction = Action.ABORT_DESTROY_BLOCK
                     PacketSender.sendPlayerAction(
                        abortAction, abortPosition!!, destroyFace
                     )
                     val rebreakStopPosition = this.miningPos
                     val rebreakStopAction = Action.STOP_DESTROY_BLOCK
                     PacketSender.sendPlayerAction(
                        rebreakStopAction, rebreakStopPosition!!, destroyFace
                     )
                     val rebreakStartPosition = this.miningPos
                     val rebreakStartAction = Action.START_DESTROY_BLOCK
                     PacketSender.sendPlayerAction(
                        rebreakStartAction, rebreakStartPosition!!, destroyFace
                     )
                     this.miningPos = null
                  }

                  // Silent swaps must be undone after the final stop, including the delayed 1.21 stop.
                  val silentSwapSetting: Setting<SpeedMineAutoSwapChoice> = this.autoSwapModeSetting
                  if (silentSwapSetting.value == SpeedMineAutoSwapChoice.SILENT && toolSlot != -1) {
                     val needsExtraTick = this.silentSwapProgress < 0.425F

                     val slotToRestore = selectedSlot

                     val restoreSlotTask = Runnable {
                        if (alternativeAllowed && this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT) {
                           InventoryUtils.swapIntoSelectedSlot(toolSlot)
                        } else if (slotToRestore != toolSlot || this.value121Setting.value == SpeedMineValue121Choice.TICK) {
                           InventoryUtils.selectHotbarSlotForPlacement(slotToRestore)
                        }
                        autoCrystalModule!!.placementGate.set(false)
                     }
                     if (needsExtraTick && this.value121Setting.value == SpeedMineValue121Choice.TICK &&
         WorldUtils.canMineBlock(brokenPosition)) {
                        swapPending.set(true)
                        val scheduler: TaskScheduler = ClientContext.taskScheduler
                        val delayedBreak = Runnable {
                           PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, brokenPosition!!, destroyFace)
                           val worldState: WorldStateManager = ClientContext.worldStateManager
                           worldState.destroyBlockPredicted(brokenPosition)
                           swapPending.set(false)
                           restoreSlotTask.run()
                        }
                        scheduler.scheduleRotationTask(delayedBreak, 1)
                     } else {
                        restoreSlotTask.run()
                     }
                  }

                  var resetAfterBreak = true
                  if ((this.rebreakSetting.value).isInstant()) {
                     if (!(miningState.block is ShulkerBoxBlock)) {
                        resetAfterBreak = false
                     } else {
                        val worldState: WorldStateManager = ClientContext.worldStateManager
                        val miningPosition = this.miningPos
                        if (!worldState.hasKnownPlacement(miningPosition!!)) {
                           resetAfterBreak = false
                        }
                     }
                  }
                  if (resetAfterBreak) {
                     reset(this)
                  }

                  if (this.rotateSetting.value) {
                     if ((RotationManager.isSilentRotationMode())) {
                        if ((WorldUtils.canMineBlock(brokenPosition))) {
                           val rotationManager: RotationManager = ClientContext.rotationManager
                           val blockCenter = brokenPosition!!.center

                           val rotations: FloatArray = RotationManager.getRotationsToPosition(
                              blockCenter)

                           rotationManager.requestRotation(rotations, 150)
                        }
                     }
                  }

                  return
               }

               return
            }
         }
      }

   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      if (event.stage == EventStage.Pre &&
         this.miningPos != null) {
         val miningPosition = this.miningPos
         if (!this.isInMiningRange(miningPosition!!)) {
            clearRebreakAndReset(this)
         }
      }
   }

   private fun recordBreakAttempt(blockPos: BlockPos) {
      if (WorldUtils.canMineBlock(blockPos)) {
         this.crystalDelayTimer.reset()
      }

      this.extraBreakTracker.trackBreak(blockPos)
   }

   private fun sendGrimStartPacket(blockPos: BlockPos, direction: Direction) {
      if (this.grimSetting.value) {
         val startAction = Action.START_DESTROY_BLOCK

         val offsetPosition = blockPos.above(1420)
         PacketSender.sendPlayerAction(startAction, offsetPosition, direction)
      }
   }

   private fun evaluateInstantBreak(blockPos: BlockPos): InstantBreakResult {
      if (noGlitchBlocksModule!!.isToggled()) {
         if (noGlitchBlocksModule!!.breakSetting.value) {
            return InstantBreakResult(false, -1, false)
         }
      }

      val miningPosition = this.miningPos
      val alternativeEnabled: Boolean = this.alternativeSetting.value
      val allowInventoryTools: Boolean = !alternativeEnabled

      val bestToolSlot: Int = InventoryUtils.findBestToolSlot(miningPosition!!, allowInventoryTools)
      val selectedSlot: Int = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
      val tool: ItemStack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(bestToolSlot)
      val state: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
      val onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
      val breakProgress: Double = WorldUtils.getBreakProgress(tool, state, onGround)
      val holdingBestTool: Boolean = bestToolSlot == selectedSlot
      if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NONE && !holdingBestTool) {
         return InstantBreakResult(false, -1, false)
      }

      if (this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT) {
         if (this.value121Setting.value != SpeedMineValue121Choice.OFF && !holdingBestTool) {
            return InstantBreakResult(false, -1, false)
         }
      }

      val damageThreshold: Float? = this.damageSetting.value
      if (breakProgress < damageThreshold!!.toDouble()) {
         return InstantBreakResult(false, -1, false)
      } else {
         return InstantBreakResult(true, bestToolSlot, breakProgress >= 1.0)
      }
   }

   private fun isRebreakPageExpanded(ignored: Any?): Boolean {
      return this.rebreakEnabledSetting.isExpanded()
   }

   private fun isInstantRebreak(ignored: Any?): Boolean {
      return (this.rebreakSetting.value).isInstant()
   }

   private fun isInstantRebreakPageExpanded(ignored: Any?): Boolean {
      return (this.rebreakSetting.value).isInstant() && this.rebreakEnabledSetting.isExpanded()
   }

   private fun isAutoSwapPageExpanded(ignored: Any?): Boolean {
      return this.autoSwapSetting.isExpanded()
   }

   private fun isNormalSwap(ignored: Any?): Boolean {
      return this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.NORMAL
   }

   private fun isSilentSwapPageExpanded(ignored: Any?): Boolean {
      return this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT && this.autoSwapSetting.isExpanded()
   }

   private fun isSwapResetVisible(ignored: Any?): Boolean {
      return this.autoSwapModeSetting.value == SpeedMineAutoSwapChoice.SILENT && this.alternativeSetting.value && this.autoSwapSetting.isExpanded()
   }

   private fun isRotatePageExpanded(ignored: Any?): Boolean {
      return this.rotateSetting.isExpanded()
   }

   private fun isRenderPageExpanded(ignored: Any?): Boolean {
      return this.renderSetting.isExpanded()
   }

   private fun isAirVisible(ignored: Any?): Boolean {
      return this.renderSetting.isExpanded() && (this.rebreakSetting.value).isInstant()
   }

   private fun isCustomColorVisible(ignored: Any?): Boolean {
      if (this.renderSetting.isExpanded()) {
         return this.colorModeSetting.value == SpeedMineColorModeChoice.CUSTOM
      }
      return false
   }

   private data class InstantBreakResult(
      val canInstantBreak: Boolean,
      val toolSlot: Int,
      val breaksInOneTick: Boolean
   ) {
   }
}
