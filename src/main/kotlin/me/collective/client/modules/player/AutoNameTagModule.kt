package me.collective.client.modules.player

import net.minecraft.world.entity.EntityTypes
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
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode
import me.collective.client.settings.options.ItemSwapMode

import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

class AutoNameTagModule : Module("AutoNameTag", "Will use name tags on nearby entities.", ModuleCategory.PLAYER) {
   val entitiesSetting: Setting<Set<EntityType<*>>>
   private val modeSetting: Setting<ListFilterMode>
   private val rangeSetting: Setting<Float>
   private val frequencySetting: Setting<Int>
   private val delaySetting: Setting<Float>
   private val rotateSetting: Setting<Boolean>
   private val swapSetting: Setting<ItemSwapMode>
   private val nameTagTimer: ElapsedTimer

   private fun isNamedNameTag(stack: ItemStack): Boolean {
      val nameTagItem: Item = Items.NAME_TAG

      if (!stack.`is`(nameTagItem)) {
         return false
      }
      val customNameType: DataComponentType<*> = DataComponents.CUSTOM_NAME
      return stack.has(customNameType)
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      if (event.stage == EventStage.Post) {
         return
      }
      val timerSnapshot: ElapsedTimer = this.nameTagTimer
      val delaySecondsSnapshot: Double = (this.delaySetting.value).toDouble()
      val delayUnit: TimeUnit = TimeUnit.SECONDS

      if (!timerSnapshot.hasElapsed(delaySecondsSnapshot, delayUnit)) {
         return
      }
      val nameTagPredicate: Predicate<ItemStack> = Predicate { stack: ItemStack -> this.isNamedNameTag(stack) }

      val nameTagSlot: Int = InventoryUtils.findHotbarSlot(nameTagPredicate)
      val previousSlot: Int = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
      var taggedCount = 0
      if (this.swapSetting.value != ItemSwapMode.NONE) {
         InventoryUtils.selectHotbarSlotForPlacement(nameTagSlot)
      }

      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         val heldStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.mainHandItem
         if (!this.isNamedNameTag(heldStack)) {
            break
         }

         val selectedMode: ListFilterMode = this.modeSetting.value

         val selectedEntityType: EntityType<*> = entity.type
         val entitiesSettingSnapshot: Setting<Set<EntityType<*>>>
   = this.entitiesSetting

         if (!ListFilterMode.matches(selectedMode, selectedEntityType, entitiesSettingSnapshot) ||
         entity is Player) {
            continue
         }
         val heldNameText: String = ClientAccess.collectiveMinecraft.player!!.mainHandItem.hoverName.getString()

         val selectedEntityText: String
         if (entity.hasCustomName()) {
            selectedEntityText = entity.getCustomName()!!.getString()
         } else {
            selectedEntityText = ""
         }

         if (heldNameText.equals(selectedEntityText, ignoreCase = true)) {
            continue
         }
         val playerEyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.eyePosition

         val entityPosition: Vec3 = entity.position()

         val distance: Double = playerEyePosition.distanceTo(entityPosition)
         val range: Float = this.rangeSetting.value
         if (distance > range) {
            continue
         }
         val interactionHand: InteractionHand = InteractionHand.MAIN_HAND

         BlockInteractionUtils.interactWithEntity(entity, interactionHand)
         taggedCount++
         if (this.rotateSetting.value) {
            val targetPosition: Vec3 = entity.position()

            val rotations: FloatArray = RotationManager.getRotationsToPosition(
               targetPosition)

            event.setRotation(rotations)
         }

         val frequency: Int = this.frequencySetting.value
         if (taggedCount >= frequency) {
            break
         }

      }

      if (this.swapSetting.value == ItemSwapMode.SILENT) {
         InventoryUtils.selectHotbarSlotForPlacement(previousSlot)
      }

      val resetTimerSnapshot: ElapsedTimer = this.nameTagTimer

      resetTimerSnapshot.reset()
   }

   init {
      this.entitiesSetting = this.add(RegistrySetSetting<EntityType<*>>("Entities", BuiltInRegistries.ENTITY_TYPE, EntityTypes.WITHER))
      this.modeSetting = this.add(EnumSetting<ListFilterMode>("Mode", ListFilterMode.WHITELIST))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 4.0F, 1.0F, 6.0F).withUnit("m"))
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 8))
      this.delaySetting = this.add(NumberSetting<Float>("Delay", 0.0F, 0.1F, 5.0F).withUnit("s"))
      this.rotateSetting = this.add(BooleanSetting("Rotate", true))
      this.swapSetting = this.add(EnumSetting<ItemSwapMode>("Swap", ItemSwapMode.NONE))
      this.nameTagTimer = ElapsedTimer()
   }

}
