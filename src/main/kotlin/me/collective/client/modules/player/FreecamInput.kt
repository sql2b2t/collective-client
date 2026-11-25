package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.movement.NoSlowModule

import me.collective.client.utils.KeyUtils
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.ClientInput
import net.minecraft.world.phys.Vec2

class FreecamInput : ClientInput(), ClientAccess {
   private fun isMovementKeyDown(mapping: KeyMapping): Boolean {
      val noSlowModule: NoSlowModule = noSlow!!
      if (noSlowModule.isToggled() && noSlowModule.isGuiMoveAllowed() && noSlowModule.guiMoveSetting.getValue()!!) {
         return KeyUtils.isPhysicallyDown(mapping)
      }
      return mapping.isDown
   }

   override fun tick() {
      val forwardKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyUp
      var positiveDown: Boolean = this.isMovementKeyDown(forwardKey)
      val backKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyDown
      var negativeDown: Boolean = this.isMovementKeyDown(backKey)

      val forward: Float = this.getAxisInput(positiveDown, negativeDown)
      val leftKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyLeft
      positiveDown = this.isMovementKeyDown(leftKey)
      val rightKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyRight
      negativeDown = this.isMovementKeyDown(rightKey)

      val sideways: Float = this.getAxisInput(positiveDown, negativeDown)
      this.leftImpulse = sideways
      this.forwardImpulse = forward
   }

   private fun getAxisInput(positiveDown: Boolean, negativeDown: Boolean): Float {
      return if (positiveDown == negativeDown) 0.0f else if (positiveDown) 1.0f else -1.0f
   }

   companion object {
      private var noSlow: NoSlowModule? = null

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         noSlow = registry.getModule(NoSlowModule::class.java) as NoSlowModule?
      }
   }
}
