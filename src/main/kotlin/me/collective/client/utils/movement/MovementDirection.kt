package me.collective.client.utils.movement

import me.collective.client.ClientAccess

import net.minecraft.world.entity.player.Input

enum class MovementDirection(
   val forwardInput: Float,
   val sidewaysInput: Float
) {
   FORWARD(1.0F, 0.0F),
   FORWARD_RIGHT(1.0F, 1.0F),
   RIGHT(0.0F, 1.0F),
   BACKWARD_RIGHT(-1.0F, 1.0F),
   BACKWARD(-1.0F, 0.0F),
   BACKWARD_LEFT(-1.0F, -1.0F),
   LEFT(0.0F, -1.0F),
   FORWARD_LEFT(1.0F, -1.0F);

   fun createInput(input: Input): Input {
      return Input(
         this.forwardInput > 0.0F,
         this.forwardInput < 0.0F,
         this.sidewaysInput > 0.0F,
         this.sidewaysInput < 0.0F,
         input.jump(),
         input.shift(),
         this.isSprintInput()
      )
   }

   private fun isSprintInput(): Boolean {
      return ClientAccess.collectiveMinecraft.player!!.isSprinting() && this.forwardInput > 0.0F
   }
}
