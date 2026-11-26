package me.collective.client.utils.entity

import me.collective.client.settings.options.SocialRole
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

interface PlayerState {
   fun `collective$setRole`(role: SocialRole)

   fun `collective$isNextToWall`(): Boolean

   fun `collective$getRole`(): SocialRole

   fun `collective$wasCrawling`(): Boolean

   fun getItemUseProgress(stack: ItemStack, entity: LivingEntity): Float {
      val elapsedMillis = (System.currentTimeMillis() - `collective$getLastEatingTime`()).toFloat()
      return elapsedMillis / (stack.getUseDuration(entity) * 50.0F + 50.0F)
   }

   fun `collective$getHealthFactor`(): Float

   fun `collective$getLastEatingTime`(): Long

   fun `collective$getLastAttackedTicksServer`(): Int

   fun `collective$resetLastAttackedServer`()
}
