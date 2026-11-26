package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.KeyboardInputTickEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.render.RenderUtils
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.PiercingWeapon
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.AABB

class LongJumpModule : Module("LongJump", "Makes you jump fast 'n far.", ModuleCategory.MOVEMENT) {
   val modeSetting: Setting<ModeChoice>
   val jumpSpeedSetting: Setting<Float>
   val inLiquidSetting: Setting<Boolean>
   val amountSetting: Setting<Int>
   val autoJumpSetting: Setting<Boolean>
   val useTimerSetting: Setting<Boolean>
   val autoDisableSetting: Setting<Boolean>
   protected var moveSpeed: Double = 0.0
   protected var previousHorizontalDistance: Double = 0.0
   protected var movementStage: Int = 0
   private var alternateBoost: Boolean = false
   private var jumpRequested: Boolean = false
   private var spearUses: Int = 0

   private fun findLungeWeapon(): InventoryUtils.SwapSlot {
      val weaponPredicate = Predicate<ItemStack> { stack: ItemStack ->
         if (ItemUtils.isSpear(stack)) {
            if (EnchantmentUtils.hasEnchantment(Enchantments.LUNGE, stack)) {
               return@Predicate true
            }
         }

         false
      }
      return InventoryUtils.findSwapSlot(weaponPredicate, true)
   }

   private fun handleSpearLanding() {
      val completedJump: Boolean = if (this.autoDisableSetting.value) {
         this.jumpRequested
      } else {
         this.spearUses > 0
      }
      if (this.autoDisableSetting.value && completedJump) {
         this.disable()
      } else {
         if (this.autoJumpSetting.value) {
            ClientContext.inputOverrides.requestJump(true)
            this.jumpRequested = true
         } else {
            this.jumpRequested = false
         }

         this.spearUses = 0
      }
   }

   override fun onEnable() {
      this.jumpRequested = false
      this.spearUses = 0
   }

   @EventHandler
   private fun onSpearTick(event: LocalPlayerPreTickEvent) {
      if (this.modeSetting.value != LongJumpModule.ModeChoice.SPEAR) {
         return
      }

      val selectedSlot: Int = InventoryUtils.getSelectedSlot()

      val weapon: InventoryUtils.SwapSlot = this.findLungeWeapon()
      if (!weapon.isFound()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
         this.handleSpearLanding()
         return
      }
      if (!this.jumpRequested) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
         return
      }

      if (PlayerStateUtils.isHungerLimited()) {
         return
      }
      val usesBeforeLunge: Int = this.spearUses
      val useLimit: Int = this.amountSetting.value
      if (usesBeforeLunge >= useLimit) {
         return
      }
      if (PlayerStateUtils.isInLiquid(ClientAccess.collectiveMinecraft.player!!)) {
         return
      }

      val attackStrength: Float = ClientAccess.collectiveMinecraft.player!!.getAttackStrengthScale(1.0f)

      if (attackStrength < 1.0f) {
         return
      }
      this.performSpearLunge(weapon, selectedSlot)
      this.spearUses++
      if (this.autoDisableSetting.value) {
         val usesAfterLunge: Int = this.spearUses
         val completedUseLimit: Int = this.amountSetting.value
         if (usesAfterLunge >= completedUseLimit) {
            this.disable()
         }
      }
   }

   private fun performSpearLunge(weapon: InventoryUtils.SwapSlot, selectedSlot: Int) {
      val componentStack: ItemStack = weapon.getStack()
      val piercingWeapon: PiercingWeapon? = (componentStack as DataComponentHolder).get(DataComponents.PIERCING_WEAPON)
      if (weapon.hand == InteractionHand.OFF_HAND) {
         val containerSlot: Int = InventoryUtils.toMenuSlot(selectedSlot)

         InventoryUtils.clickSlot(containerSlot, 40, ContainerInput.SWAP)
      } else {
         InventoryUtils.swapTo(weapon)
      }

      ClientAccess.collectiveMinecraft.gameMode!!.piercingAttack(piercingWeapon!!)
      ClientAccess.collectiveMinecraft.player!!.swing(InteractionHand.MAIN_HAND)
      ClientAccess.collectiveMinecraft.player!!.resetOnlyAttackStrengthTicker()
      if (weapon.hand == InteractionHand.OFF_HAND) {
         val convertedRestoreSlot: Int = InventoryUtils.toMenuSlot(selectedSlot)

         InventoryUtils.clickSlot(convertedRestoreSlot, 40, ContainerInput.SWAP)
      } else {
         InventoryUtils.swapBack(selectedSlot, weapon)
      }
   }

   @EventHandler(priority = 100)
   private fun onJumpInput(event: KeyboardInputTickEvent) {
      if (event.input.keyPresses.jump()) {
         this.jumpRequested = true
      }
   }

   @EventHandler(priority = 100)
   private fun measureHorizontalDistance(event: LocalPlayerSendPositionEvent) {
      if (event.stage != EventStage.Post) {
         if (this.modeSetting.value == LongJumpModule.ModeChoice.PLAIN) {
            if (!MovementPhysics.isLocalPlayerMoving()) {
               this.movementStage = 4
            }

            val deltaX: Double = ClientAccess.collectiveMinecraft.player!!.getX()
               - ClientAccess.collectiveMinecraft.player!!.xo
            val deltaZ: Double = ClientAccess.collectiveMinecraft.player!!.getZ()
               - ClientAccess.collectiveMinecraft.player!!.zo
            val distanceSquared: Double = deltaX * deltaX + deltaZ * deltaZ
            this.previousHorizontalDistance = Math.sqrt(distanceSquared)
         }
      }
   }

   @EventHandler
   private fun applyPlainMovement(event: LocalPlayerMoveEvent) {
      if (this.modeSetting.value != ModeChoice.PLAIN || this.collidesWithSuffocatingColumn() ||
         this.isLiquidBlocked() || ClientAccess.collectiveMinecraft.player!!.isFallFlying() ||
         ClientAccess.collectiveMinecraft.player!!.isSpectator()) {
         return
      }
      var motionY: Double = event.y
      if (this.useTimerSetting.value) {
         if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
            ClientContext.tickRateController.requestMultiplier(this, 1.0888f)
         } else {
            ClientContext.tickRateController.releaseMultiplier(this)
         }
      }
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L) && this.autoDisableSetting.value) {
         this.toggle()
      }
      val jumpSpeedScale: Double = (2.0f * this.jumpSpeedSetting.value).toDouble()
      val baseSpeed: Double = 0.2873 * jumpSpeedScale
      if (this.movementStage == 1 && MovementPhysics.isLocalPlayerMoving()) {
         this.moveSpeed = 1.35 * MovementPhysics.applySpeedEffects(false, baseSpeed) - 0.01
      } else if (this.movementStage == 2 && MovementPhysics.isLocalPlayerMoving()) {
         motionY = 0.42 + MovementPhysics.getJumpBoost()
         this.moveSpeed *= if (this.alternateBoost) 1.6835 else 1.395
      } else if (this.movementStage == 3) {
         this.moveSpeed = this.previousHorizontalDistance
            - 0.6600000262260437 * (this.previousHorizontalDistance - MovementPhysics.applySpeedEffects(true, baseSpeed))
         this.alternateBoost = !this.alternateBoost
      } else {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (player.verticalCollision ||
         !ClientAccess.collectiveMinecraft.level!!.noCollision(player.getBoundingBox().move(0.0, player.getDeltaMovement().y, 0.0))) {
            if (this.movementStage > 0) {
               this.movementStage = if (MovementPhysics.isLocalPlayerMoving()) 1 else 0
            }
         }
         this.moveSpeed = this.previousHorizontalDistance - this.previousHorizontalDistance / 159.0
      }
      this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.applySpeedEffects(false, baseSpeed))
      var motionX: Double = 0.0
      var motionZ: Double = 0.0
      if (MovementPhysics.isLocalPlayerMoving()) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val yaw: Float = player.getViewYRot(RenderUtils.getPartialTick())
         val horizontal: DoubleArray = MovementPhysics.getHorizontalMotion(yaw, player.input, this.moveSpeed)
         motionX = horizontal[0]
         motionZ = horizontal[1]
      }
      Vec3Utils.setComponents(event.velocity, motionX, motionY, motionZ)
      if (MovementPhysics.isLocalPlayerMoving()) {
         this.movementStage++
      }
   }

   fun collidesWithSuffocatingColumn(): Boolean {
      val playerBounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
      val columnBox = AABB(
         ClientAccess.collectiveMinecraft.player!!.blockPosition().getX().toDouble(),
         playerBounds.minY,
         ClientAccess.collectiveMinecraft.player!!.blockPosition().getZ().toDouble(),
         ClientAccess.collectiveMinecraft.player!!.blockPosition().getX() + 1.0,
         playerBounds.maxY,
         ClientAccess.collectiveMinecraft.player!!.blockPosition().getZ() + 1.0
      )

      val inset: Double = 1e-07
      val collisionBox: AABB = columnBox.deflate(inset)
      return ClientAccess.collectiveMinecraft.level!!.collidesWithSuffocatingBlock(ClientAccess.collectiveMinecraft.player!!, collisionBox)
   }


   fun isLiquidBlocked(): Boolean {
      if (PlayerStateUtils.isInLiquid(ClientAccess.collectiveMinecraft.player!!)) {
         if (!this.inLiquidSetting.value) {
            return true
         }
      }

      return false
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PLAIN))
      val jumpSpeedVisible = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.jumpSpeedSetting = this.add(NumberSetting<Float>("JumpSpeed", 1.0f, 0.0f, 5.0f, jumpSpeedVisible))
      val inLiquidVisible = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.inLiquidSetting = this.add(BooleanSetting("InLiquid", false, inLiquidVisible))
      val amountVisible = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.SPEAR }
      this.amountSetting = this.add(NumberSetting<Int>("Amount", 2, 1, 4, amountVisible))
      val autoJumpVisible = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.SPEAR }
      this.autoJumpSetting = this.add(BooleanSetting("AutoJump", true, autoJumpVisible))
      this.useTimerSetting = this.add(BooleanSetting("UseTimer", true))
      this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", true))

      this.movementStage = 4
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      SPEAR("Spear");

      override fun getName(): String {
         return this.displayName
      }
   }
}
