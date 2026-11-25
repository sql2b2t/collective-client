package me.collective.client.modules.player


import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.animal.Cat
import net.minecraft.world.entity.animal.Parrot
import net.minecraft.world.entity.animal.Wolf
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private fun createTamingFoodPredicate(animal: TamableAnimal): Predicate<ItemStack> {
   if (animal is Wolf) {
      return Predicate { stack: ItemStack ->
         val bone: Item = Items.BONE
         stack.`is`(bone)
      }
   }
   if (animal is Parrot) {
      return Predicate { stack: ItemStack ->
         val beetrootSeeds: Item = Items.BEETROOT_SEEDS
         val pumpkinSeeds: Item = Items.PUMPKIN_SEEDS
         val melonSeeds: Item = Items.MELON_SEEDS
         val wheatSeeds: Item = Items.WHEAT_SEEDS
         listOf(wheatSeeds, melonSeeds, pumpkinSeeds, beetrootSeeds).contains(stack.item)
      }
   }
   return Predicate { stack: ItemStack -> animal.isFood(stack) }
}

class AutoTameModule : Module("AutoTame", "Automatically interacts with tame-able entities.", ModuleCategory.PLAYER) {
   private val actionSetting: Setting<ActionChoice>
   private val rangeSetting: Setting<Float>
   private val frequencySetting: Setting<Int>
   private val delaySetting: Setting<Float>
   private val rotateSetting: Setting<Boolean>
   internal val targetsSetting: Setting<Boolean>
   private val dogsSetting: Setting<Boolean>
   private val catsSetting: Setting<Boolean>
   private val parrotsSetting: Setting<Boolean>
   private val interactionTimer: ElapsedTimer

   private fun isTargetEnabled(animal: TamableAnimal): Boolean {
      return (animal is Cat && this.catsSetting.value) ||
         (animal is Wolf && this.dogsSetting.value) ||
         (animal is Parrot && this.parrotsSetting.value)
   }

   @EventHandler
   private fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (event.stage == EventStage.Post) {
         return
      }
      val timerSnapshot: ElapsedTimer = this.interactionTimer
      val delaySeconds: Double = (this.delaySetting.value).toDouble()
      val delayUnit: TimeUnit = TimeUnit.SECONDS

      if (!timerSnapshot.hasElapsed(delaySeconds, delayUnit)) {
         return
      }
      var interactionCount = 0
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         val animal = entity as? TamableAnimal ?: continue
         if (!this.isTargetEnabled(animal)) {
            continue
         }
         val distancePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!

         val distance: Float = entity.distanceTo(distancePlayer)
         val range: Float = this.rangeSetting.value
         if (!(distance <= range)) {
            continue
         }

         val interactionPosition: Vec3? = this.actionSetting.value.interact(animal)
         if (interactionPosition == null) {
            continue
         }
         interactionCount++
         if (this.rotateSetting.value) {
            val rotations: FloatArray = RotationManager.getRotationsToPosition(
               interactionPosition)

            event.setRotation(rotations)
         }
         val frequency: Int = this.frequencySetting.value
         if (interactionCount >= frequency) {
            break
         }

      }
      val resetTimerSnapshot: ElapsedTimer = this.interactionTimer
      resetTimerSnapshot.reset()
   }

   init {
      this.actionSetting = this.add(EnumSetting<ActionChoice>("Action", ActionChoice.TAME))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 3.0F, 1.0F, 6.0F).withUnit("m"))
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 8))
      this.delaySetting = this.add(NumberSetting<Float>("Delay", 0.1F, 0.1F, 5.0F).withUnit("s"))
      this.rotateSetting = this.add(BooleanSetting("Rotate", true))
      val targetsBuilder = BooleanSetting("Targets", false)
      targetsBuilder.markAsGroup()
      targetsBuilder.markVisibilityDependency()
      this.targetsSetting = this.add(targetsBuilder)
      val dogsVisibility: Predicate<Boolean> = Predicate { ignoredValue: Boolean ->
         val targets: Setting<Boolean> = this.targetsSetting
         targets.isExpanded
      }
      this.dogsSetting = this.add(BooleanSetting("Dogs", true, dogsVisibility))
      val catsVisibility: Predicate<Boolean> = Predicate { ignoredValue: Boolean ->
         val targets: Setting<Boolean> = this.targetsSetting
         targets.isExpanded
      }
      this.catsSetting = this.add(BooleanSetting("Cats", true, catsVisibility))
      val parrotsVisibility: Predicate<Boolean> = Predicate { ignoredValue: Boolean ->
         val targets: Setting<Boolean> = this.targetsSetting
         targets.isExpanded
      }
      this.parrotsSetting = this.add(BooleanSetting("Parrots", true, parrotsVisibility))
      this.interactionTimer = ElapsedTimer()
   }

   private enum class ActionChoice(private val displayName: String) : NamedEntry {
      TAME("Tame") {
         override fun interact(animal: TamableAnimal): Vec3? {
            if (animal.owner != null) {
               return null
            }

            val foodPredicate: Predicate<ItemStack> = createTamingFoodPredicate(animal)
            val slot: Int = InventoryUtils.findHotbarSlot(foodPredicate)
            if (slot == -1) {
               return null
            }

            InventoryUtils.selectHotbarSlotForPlacement(slot)
            val hand: InteractionHand = InteractionHand.MAIN_HAND

            BlockInteractionUtils.interactWithEntity(animal, hand)

            val bounds: AABB = animal.boundingBox
            return bounds.center
         }

      },
      SIT("Sit") {
         override fun interact(animal: TamableAnimal): Vec3? {
            if (animal.isInSittingPose) {
               return null
            }
            if (animal.isOrderedToSit || ClientAccess.collectiveMinecraft.player!! != animal.owner) {
               return null
            }
            return this.interactUnlessHoldingFood(animal)
         }

      },
      STAND("Stand") {
         override fun interact(animal: TamableAnimal): Vec3? {
            if (!animal.isInSittingPose && !animal.isOrderedToSit) {
               return null
            }
            if (ClientAccess.collectiveMinecraft.player!! != animal.owner) {
               return null
            }
            return this.interactUnlessHoldingFood(animal)
         }

      };

      fun interactUnlessHoldingFood(animal: TamableAnimal): Vec3? {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val heldStack: ItemStack = player.mainHandItem
         if (animal.isFood(heldStack)) {
            return null
         }
         val hand: InteractionHand = InteractionHand.MAIN_HAND
         BlockInteractionUtils.interactWithEntity(animal, hand)
         return animal.boundingBox.center
      }

      override fun getName(): String {
         return this.displayName
      }

      abstract fun interact(animal: TamableAnimal): Vec3?
   }

}
