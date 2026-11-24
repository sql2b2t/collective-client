package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.Iterator
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.equine.Donkey
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.animal.feline.Cat
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

class AutoBreedModule : Module("AutoBreed", "Breeds animals in between each other automatically.", ModuleCategory.PLAYER) {
   private val rangeSetting: Setting<Float>
   private val frequencySetting: Setting<Int>
   private val delaySetting: Setting<Float>
   private val rotateSetting: Setting<Boolean>
   private val targetsSetting: Setting<Boolean>
   private val dogsSetting: Setting<Boolean>
   private val catsSetting: Setting<Boolean>
   private val parrotsSetting: Setting<Boolean>
   private val donkeysSetting: Setting<Boolean>
   private val horsesSetting: Setting<Boolean>
   private val allaysSetting: Setting<Boolean>
   private val othersSetting: Setting<Boolean>
   private val interactionTimer: ElapsedTimer
   private val breedingCooldowns: MutableList<BreedingCooldown>

   override fun onEnable() {
      this.breedingCooldowns.clear()
   }

   private fun getBreedingFoodPredicate(entity: Entity): Predicate<ItemStack> {
      if (entity is Allay) {
         return Predicate { stack: ItemStack ->
            val foodItem: Item = Items.AMETHYST_SHARD
            stack.`is`(foodItem)
         }
      } else if (entity is Parrot) {
         return Predicate { stack: ItemStack ->
            listOf(Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS).contains(stack.item)
         }
      } else if (entity is Animal) {
         val animal: Animal = entity
         return Predicate { stack: ItemStack -> animal.isFood(stack) }
      } else {
         return Predicate { stack: ItemStack -> false }
      }
   }

   @EventHandler
   private fun onRotationRequest(event: RotationRequestEvent) {
      this.breedingCooldowns.forEach { cooldown -> cooldown.elapsedTicks++ }
      this.breedingCooldowns.removeIf { cooldownEntry ->
         val elapsedTicks: Int = cooldownEntry.elapsedTicks

         elapsedTicks >= 6000
      }
      val delayTimer: ElapsedTimer = this.interactionTimer
      val delaySecondsValue: Double = (this.delaySetting.value).toDouble()
      val delayUnit: TimeUnit = TimeUnit.SECONDS

      if (delayTimer.hasElapsed(delaySecondsValue, delayUnit)) {
         var interactions = 0
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val entities = level.entitiesForRendering().iterator()

         entityScan@ while (entities.hasNext()) {
            val livingEntity = entities.next() as? LivingEntity ?: continue@entityScan
            val cooldowns = this.breedingCooldowns.iterator()

            while (cooldowns.hasNext()) {
               val cooldown: BreedingCooldown = cooldowns.next()

               if (livingEntity == cooldown.entity) {
                  continue@entityScan
               }
            }

            if (this.isTargetEnabled(livingEntity)) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               val distance: Float = livingEntity.distanceTo(player)
               val rangeValue: Float = this.rangeSetting.value
               if (distance <= rangeValue) {
                  val foodPredicate: Predicate<ItemStack> = getBreedingFoodPredicate(livingEntity)

                  val foodSlot: Int = InventoryUtils.findHotbarSlot(foodPredicate)
                  if (foodSlot == -1) {
                     // Нет еды для этого животного — проверяем остальных,
                     // а не выходим из скана (иначе пропускается и сброс таймера).
                     continue@entityScan
                  }

                  val targetCenter: Vec3? = livingEntity.boundingBox.center
                  if (targetCenter != null) {
                     InventoryUtils.selectHotbarSlotForPlacement(foodSlot)
                     val hand: InteractionHand = InteractionHand.MAIN_HAND
                     BlockInteractionUtils.interactWithEntity(livingEntity, hand)
                     this.breedingCooldowns.add(BreedingCooldown(livingEntity))
                     interactions++
                     if (this.rotateSetting.value) {
                        val rotationManagerForRequest = ClientContext.rotationManager

                        val rotations: FloatArray = RotationManager.getRotationsToPosition(
                           targetCenter)

                        rotationManagerForRequest.requestRotation(rotations, 5)
                     }

                     val frequencyValue: Int = this.frequencySetting.value
                     if (interactions >= frequencyValue) {
                        break
                     }
                  }
               }
            }
         }

         val resetTimer: ElapsedTimer = this.interactionTimer

         resetTimer.reset()
      }
   }

   private fun isTargetEnabled(entity: LivingEntity): Boolean {
      if (entity.isBaby) {
         return false
      }

      return when (entity) {
         is Wolf -> this.dogsSetting.value
         is Cat -> this.catsSetting.value
         is Parrot -> this.parrotsSetting.value
         is Donkey -> this.donkeysSetting.value
         is Horse -> this.horsesSetting.value
         is Allay -> this.allaysSetting.value
         is Animal -> this.othersSetting.value
         else -> false
      }
   }

   init {
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 3.0F, 1.0F, 6.0F).withUnit("m"))
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 8))
      this.delaySetting = this.add(NumberSetting<Float>("Delay", 0.0F, 0.0F, 5.0F).withUnit("s"))
      this.rotateSetting = this.add(BooleanSetting("Rotate", true))
      val targetsOption = BooleanSetting("Targets", false)
      targetsOption.markAsGroup()
      targetsOption.markVisibilityDependency()
      this.targetsSetting = this.add(targetsOption)
      this.dogsSetting = this.add(BooleanSetting("Dogs", true).dependsOn(this.targetsSetting))
      this.catsSetting = this.add(BooleanSetting("Cats", true).dependsOn(this.targetsSetting))
      this.parrotsSetting = this.add(BooleanSetting("Parrots", true).dependsOn(this.targetsSetting))
      this.donkeysSetting = this.add(BooleanSetting("Donkeys", true).dependsOn(this.targetsSetting))
      this.horsesSetting = this.add(BooleanSetting("Horses", true).dependsOn(this.targetsSetting))
      this.allaysSetting = this.add(BooleanSetting("Allays", false).dependsOn(this.targetsSetting))
      this.othersSetting = this.add(BooleanSetting("Others", true).dependsOn(this.targetsSetting))
      this.interactionTimer = ElapsedTimer()
      this.breedingCooldowns = ArrayList()
   }

   private class BreedingCooldown(val entity: LivingEntity) {
      var elapsedTicks: Int = 0
   }

}
