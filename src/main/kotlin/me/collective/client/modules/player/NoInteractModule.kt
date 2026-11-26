package me.collective.client.modules.player


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ProjectileItem
import net.minecraft.world.item.ProjectileWeaponItem
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState

class NoInteractModule : Module("NoInteract", "Prevents you from interacting with tile entities.", ModuleCategory.PLAYER) {
   @JvmField
   val targetsSetting: Setting<TargetsChoice>
   @JvmField
   val modeSetting: Setting<ModeChoice>
   @JvmField
   val onlyFoodSetting: Setting<Boolean>
   @JvmField
   var shouldStopSneaking: Boolean = false

   private fun isSpawnPointTarget(state: BlockState): Boolean {
      if (ClientContext.worldStateManager.dimension.isOverworld()) {
         return state.block is BedBlock
      }
      if (ClientContext.worldStateManager.dimension.isNether()) {
         return state.block is RespawnAnchorBlock
      }
      return false
   }

   fun shouldBlockInteraction(stack: ItemStack, position: BlockPos): Boolean {
      if (!this.isToggled()) {
         return false
      }
      if (this.onlyFoodSetting.value!!) {
         val foodComponent: DataComponentType<*> = DataComponents.FOOD
         if (!stack.has(foodComponent)) {
            return false
         }
      }
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val state = level.getBlockState(position)
      if (this.targetsSetting.value === TargetsChoice.SPAWN_POINT) {
         return this.isSpawnPointTarget(state)
      }
      if (this.targetsSetting.value === TargetsChoice.BLOCKS) {
         return this.isBlockInteractionTarget(state)
      }
      return this.isBlockInteractionTarget(state) || this.isSpawnPointTarget(state)
   }

   private fun isBlockInteractionTarget(state: BlockState): Boolean {
      if (state.block is AnvilBlock) {
         return true
      }

      return state.hasBlockEntity() && state.block !is BedBlock
   }

   init {
      val targetsName = "Targets"
      val defaultTargets = TargetsChoice.BLOCKS
      val targetsOption = EnumSetting<TargetsChoice>(targetsName, defaultTargets)
      this.targetsSetting = this.add(targetsOption)
      val modeName = "Mode"
      val defaultMode = ModeChoice.CANCEL
      val modeOption = EnumSetting<ModeChoice>(modeName, defaultMode)
      this.modeSetting = this.add(modeOption)
      val onlyFoodName = "OnlyFood"
      val defaultOnlyFood = false
      val onlyFoodOption = BooleanSetting(onlyFoodName, defaultOnlyFood)
      this.onlyFoodSetting = this.add(onlyFoodOption)
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      CANCEL("Cancel"),
      SHIFT("Shift");

      override fun getName(): String {
         return this.displayName
      }

   }

   enum class TargetsChoice(private val displayName: String) : NamedEntry {
      BLOCKS("Blocks"),
      SPAWN_POINT("SpawnPoint"),
      BOTH("Both");

      override fun getName(): String {
         return this.displayName
      }

   }

   companion object {
      @JvmStatic
      fun selectUsableHand(): InteractionHand {
         val mainHandStack = ClientAccess.collectiveMinecraft.player!!.mainHandItem
         val offhandStack = ClientAccess.collectiveMinecraft.player!!.offhandItem

         if (!isFoodOrProjectile(mainHandStack) && isFoodOrProjectile(offhandStack)) {
            return InteractionHand.OFF_HAND
         }

         return InteractionHand.MAIN_HAND
      }

      @JvmStatic
      fun isFoodOrProjectile(stack: ItemStack): Boolean {
         val foodComponent: DataComponentType<*> = DataComponents.FOOD
         if (stack.has(foodComponent)) {
            return true
         }
         return stack.item is EnderpearlItem ||
         stack.item is ProjectileItem ||
         stack.item is ProjectileWeaponItem
      }
   }
}
