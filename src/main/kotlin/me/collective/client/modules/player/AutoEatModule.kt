package me.collective.client.modules.player

import java.util.function.Predicate

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.managers.SocialRegistry
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.inventory.CustomItemRegistries
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodData
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.Consumable
import net.minecraft.world.item.component.SuspiciousStewEffects
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect
import net.minecraft.world.item.consume_effects.ConsumeEffect

class AutoEatModule : Module("AutoEat", "Eats your food for you.", ModuleCategory.PLAYER) {
   private val itemsSetting: Setting<Set<Item>>
   private val modeSetting: Setting<ListFilterMode>
   private val healthSetting: Setting<Int>
   private val hungerSetting: Setting<Int>
   private val untilFullSetting: Setting<Boolean>
   private val pauseBaritoneSetting: Setting<Boolean>
   private val autoSwapSetting: Setting<Boolean>
   private val sortSetting: Setting<SortChoice>
   private val swapBackSetting: Setting<Boolean>
   private val autoEscapeSetting: Setting<Boolean>
   private val autoDisableSetting: Setting<Boolean>
   private var previousSelectedSlot: Int = 0
   private var eating: Boolean = false
   private var continueUntilFull: Boolean = false
   private var swappedContainerSlot: Int = 0

   override fun onToggle() {
      this.setEating(false)
      this.previousSelectedSlot = -1
      this.swappedContainerSlot = -1
   }

   override fun onDisable() {
      ClientAccess.collectiveMinecraft.options.keyUse.setDown(false)
      ClientContext.baritoneBridge.releasePause(this)
   }

   private fun swapWithSelectedHotbarSlot(containerSlot: Int) {
      val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
      val containerId: Int = ClientAccess.collectiveMinecraft.player!!.containerMenu.containerId
      val selectedHotbarSlot: Int = ClientAccess.collectiveMinecraft.player!!.getInventory().getSelectedSlot()
      val clickType: ContainerInput = ContainerInput.SWAP
      val clickPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      gameMode.handleContainerInput(containerId, containerSlot, selectedHotbarSlot, clickType, clickPlayer)
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      val postStage: EventStage = EventStage.Post
      if (event.stage == postStage) {
         return
      }
      if (this.continueUntilFull) {
         val foodData: FoodData = ClientAccess.collectiveMinecraft.player!!.getFoodData()
         if (!foodData.needsFood()) {
            this.continueUntilFull = false
         }
      }

      ClientContext.baritoneBridge.releasePause(this)
      val initialAutoSwapEnabled: Boolean = this.autoSwapSetting.getValue()!!
      if (!initialAutoSwapEnabled && this.eating) {
         val heldMainHandStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getMainHandItem()
         if (!this.canEat(heldMainHandStack)) {
            val invalidStackGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            val invalidStackPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            invalidStackGameMode.releaseUsingItem(invalidStackPlayer)
            this.setEating(false)
            return
         }
      }

      var foodSlot: Int = this.findFoodSlot()
      escapeFoodSelection@ do {
         if (foodSlot != -1) {
            val candidateStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getInventory().getItem(foodSlot)
            val chorusFruitToCompare: Item = Items.CHORUS_FRUIT
            if (candidateStack.`is`(chorusFruitToCompare)) {
               break@escapeFoodSelection
            }
         }

         if (this.shouldEscape()) {
            val chorusFruitToFind: Item = Items.CHORUS_FRUIT
            val chorusContainerSlot: Int = InventoryUtils.findMenuSlot(chorusFruitToFind)
            if (chorusContainerSlot != -1) {
               foodSlot = -1
               this.swappedContainerSlot = chorusContainerSlot
               val escapeContainerSlot: Int = this.swappedContainerSlot
               this.swapWithSelectedHotbarSlot(escapeContainerSlot)
            }
         }
      } while (false)

      if (this.shouldStopEating()) {
         if (!this.shouldEscape()) {
            val useTicksAtThreshold: Boolean = ClientAccess.collectiveMinecraft.player!!.getTicksUsingItem() >= 2
            if (this.eating && useTicksAtThreshold) {
               val stopUseGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
               val stopUsePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               stopUseGameMode.releaseUsingItem(stopUsePlayer)
               this.setEating(false)
               if (this.swappedContainerSlot != -1) {
                  // Сначала возвращаем еду из контейнера в слот, откуда она взята,
                  // и только потом восстанавливаем выбранный хотбар-слот: в обратном
                  // порядке SWAP уходил в чужой слот и инвентарь перемешивался.
                  val restoredContainerSlot: Int = this.swappedContainerSlot
                  this.swapWithSelectedHotbarSlot(restoredContainerSlot)
                  this.swappedContainerSlot = -1
               }

               if (this.autoSwapSetting.getValue()!!) {
                  if ((this.swapBackSetting.getValue()!!) && this.previousSelectedSlot != -1) {
                     val previousHotbarSlot: Int = this.previousSelectedSlot
                     InventoryUtils.selectHotbarSlotForPlacement(previousHotbarSlot)
                     this.previousSelectedSlot = -1
                  }
               }

               if (this.autoDisableSetting.getValue()!!) {
                  if (this.autoEscapeSetting.getValue()!!) {
                     this.disable()
                  }
               }
            }
            return
         }
      }

      val useOffHand: Boolean = foodSlot == 40
      if ((this.autoSwapSetting.getValue()!!) && !useOffHand && foodSlot != -1) {
         if (ClientAccess.collectiveMinecraft.player!!.getInventory().getSelectedSlot() != foodSlot) {
            this.previousSelectedSlot = ClientAccess.collectiveMinecraft.player!!.getInventory().getSelectedSlot()
            InventoryUtils.selectHotbarSlotForPlacement(foodSlot)
         }
      }

      val mainHandStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getMainHandItem()
      val useStatePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (!useStatePlayer.isUsingItem && (useOffHand || this.canEat(mainHandStack))) {
         val useItemGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
         val useItemPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val useHand: InteractionHand = if (useOffHand) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
         useItemGameMode.useItem(useItemPlayer, useHand)
         this.setEating(true)
      }

      if (this.pauseBaritoneSetting.getValue()!!) {
         if (ClientAccess.collectiveMinecraft.player!!.isUsingItem) {
            ClientContext.baritoneBridge.requestPause(this)
         }
      }
   }

   fun isEating(): Boolean {
      return this.eating
   }

   private fun setEating(requestedEating: Boolean) {
      if (this.eating == requestedEating) {
         return
      }
      if (requestedEating && this.isUntilFullEnabled()) {
         if (ClientAccess.collectiveMinecraft.player!!.getFoodData().needsFood()) {
            this.continueUntilFull = true
         }
      }
      this.eating = requestedEating
   }

   private fun findFoodSlot(): Int {
      var bestScore: Double = -1.0
      var selectedFoodSlot: Int = -1
      var scanSlot: Int = 0
      while (true) {
         if (scanSlot >= 10) {
            break
         }
         if (scanSlot == 9) {
            scanSlot = 40
         }
         val stack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getInventory().getItem(scanSlot)
         val foodProperties: FoodProperties? = stack.get(DataComponents.FOOD)
         if (!this.canEat(stack)) {
            scanSlot++
            continue
         }
         val candidateScore: Double = (this.sortSetting.getValue()!!).score(stack, foodProperties!!)
         var betterScore: Boolean = candidateScore > bestScore
         if (!betterScore && candidateScore == bestScore) {
            val tieBreakInventory: Inventory = ClientAccess.collectiveMinecraft.player!!.getInventory()
            betterScore = scanSlot == tieBreakInventory.getSelectedSlot()
         }
         if (betterScore) {
            bestScore = candidateScore
            selectedFoodSlot = scanSlot
         }
         if (stack.getItem() === Items.CHORUS_FRUIT) {
            if (this.shouldEscape()) {
               selectedFoodSlot = scanSlot
               break
            }
         }

         scanSlot++
      }
      return selectedFoodSlot
   }

   private fun canEat(stack: ItemStack): Boolean {
      val foodProperties: FoodProperties? = stack.get(DataComponents.FOOD)
      if (foodProperties == null) {
         return false
      }
      if (!foodProperties.canAlwaysEat()) {
         if (ClientAccess.collectiveMinecraft.player!!.getFoodData().getFoodLevel() >= 20) {
            return false
         }
      }
      val chorusFruit: Item = Items.CHORUS_FRUIT
      if (stack.`is`(chorusFruit)) {
         if (!this.shouldEscape()) {
            return false
         }
      }
      val selectionMode: ListFilterMode = this.modeSetting.getValue()!!
      val item: Item = stack.getItem()
      if (!ListFilterMode.matches(selectionMode, item, this.itemsSetting.getValue())) {
         return false
      }
      val stewEffects: SuspiciousStewEffects? = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS)
      if (stewEffects != null) {
         for (stewEffect in stewEffects.effects()) {
            val mobEffect: MobEffect = stewEffect.effect().value()
            if (!mobEffect.isBeneficial) {
               return false
            }
         }
      }
      return true
   }

   private fun shouldEscape(): Boolean {
      val autoEscapeEnabled: Boolean = this.autoEscapeSetting.getValue()!!
      if (!autoEscapeEnabled) {
         return false
      }
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (candidatePlayer in level.players()) {
         val localPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val distanceToLocalPlayer: Float = candidatePlayer.distanceTo(localPlayer)
         val escapeRadius: Float = 12.0f
         if (distanceToLocalPlayer <= escapeRadius) {
            val playerExclusionFilter: SocialRegistry = ClientContext.socialRegistry
            if (!playerExclusionFilter.isFriendPlayer(candidatePlayer) &&
         candidatePlayer !== ClientAccess.collectiveMinecraft.player!!) {
               return ClientAccess.collectiveMinecraft.player!!.tickCount <= 50
            }
         }
      }
      return false
   }

   private fun isUntilFullEnabled(): Boolean {
      return this.untilFullSetting.getValue()!! && this.untilFullSetting.isVisible()
   }

   private fun shouldStopEating(): Boolean {
      if (this.continueUntilFull && this.isUntilFullEnabled()) {
         return false
      }
      if ((this.hungerSetting.getValue()!!) != -1) {
         val foodLevel: Int = ClientAccess.collectiveMinecraft.player!!.getFoodData().getFoodLevel()
         val hungerThreshold: Int = this.hungerSetting.getValue()!!
         if (foodLevel <= hungerThreshold) {
            return false
         }
      }
      if ((this.healthSetting.getValue()!!) == 0) {
         return true
      }
      val healthAndAbsorption: Float = EntityUtils.getPlayerHealthWithAbsorption()
      val healthThreshold: Int = this.healthSetting.getValue()!!
      return healthAndAbsorption > healthThreshold.toFloat()
   }

   init {
      val itemRegistry: DefaultedRegistry<Item> = CustomItemRegistries.foodItems
      val defaultItems: Array<Item> = arrayOf(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE)
      this.itemsSetting = this.add(RegistrySetSetting<Item>("Items", itemRegistry, *defaultItems))
      this.modeSetting = this.add(EnumSetting<ListFilterMode>("Mode", ListFilterMode.ANY))
      val health: NumberSetting<Int> = NumberSetting<Int>("Health", 16, 0, 36)
      val healthOffBound: SettingLabelCondition = SettingLabelCondition.MIN
      this.healthSetting = this.add(health.setBoundaryLabel("Off", healthOffBound))
      val hunger: NumberSetting<Int> = NumberSetting<Int>("Hunger", 6, -1, 20)
      val hungerOffBound: SettingLabelCondition = SettingLabelCondition.MIN
      this.hungerSetting = this.add(hunger.setBoundaryLabel("Off", hungerOffBound))
      this.untilFullSetting = this.add(BooleanSetting("UntilFull", false))
      this.pauseBaritoneSetting = this.add(BooleanSetting("PauseBaritone", false))
      this.autoSwapSetting = this.add(BooleanSetting("AutoSwap", true).markVisibilityDependency())
      val defaultSort: AutoEatModule.SortChoice = AutoEatModule.SortChoice.SATURATION
      this.sortSetting = this.add(EnumSetting<SortChoice>("Sort", defaultSort).dependsOn(this.autoSwapSetting))
      this.swapBackSetting = this.add(BooleanSetting("SwapBack", false).dependsOn(this.autoSwapSetting))
      this.autoEscapeSetting = this.add(BooleanSetting("AutoEscape", false).markVisibilityDependency())
      this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", false).dependsOn(this.autoEscapeSetting))
      this.untilFullSetting.addVisibilityPredicate(Predicate { ignored -> !this.hungerSetting.isAtMinimum() })
      this.pauseBaritoneSetting.hideWithoutBaritone()
   }

   private enum class SortChoice(private val displayName: String) : NamedEntry {
      HUNGER("Hunger") {
         override fun score(unusedStack: ItemStack, foodProperties: FoodProperties): Double {
            return foodProperties.nutrition().toDouble()
         }
      },
      SATURATION("Saturation") {
         override fun score(unusedStack: ItemStack, foodProperties: FoodProperties): Double {
            return foodProperties.saturation().toDouble()
         }
      },
      HEALTH("Health") {
         override fun score(stack: ItemStack, unusedFoodProperties: FoodProperties): Double {
            val consumableComponent = stack.get(DataComponents.CONSUMABLE)
            if (consumableComponent is Consumable) {
               val consumable: Consumable = consumableComponent
               for (consumeEffect in consumable.onConsumeEffects()) {
                  if (consumeEffect is ApplyStatusEffectsConsumeEffect) {
                     val statusEffects: ApplyStatusEffectsConsumeEffect = consumeEffect
                     val regenerationAmplifier: Int = getRegenerationAmplifier(statusEffects)
                     if (regenerationAmplifier > 0) {
                        return regenerationAmplifier.toDouble()
                     }
                  }
               }
            }

            return 0.0
         }

         private fun getRegenerationAmplifier(statusEffects: ApplyStatusEffectsConsumeEffect): Int {
            for (effect in statusEffects.effects()) {
               if (effect.getEffect() === MobEffects.REGENERATION) {
                  return effect.getAmplifier()
               }
            }

            return 0
         }
      };

      override fun getName(): String {
         return this.displayName
      }

      abstract fun score(
         stack: ItemStack, foodProperties: FoodProperties
      ): Double
   }
}
