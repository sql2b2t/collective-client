package me.collective.client.utils

import me.collective.mixin.InputAccessor
import net.minecraft.client.player.ClientInput
import net.minecraft.world.phys.Vec2

class InputUtils private constructor() {
   companion object {
      @JvmStatic
      fun setSneak(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setSneak`(pressed)
      }

      @JvmStatic
      fun setJump(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setJump`(pressed)
      }

      @JvmStatic
      fun setForward(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setForward`(pressed)
      }

      @JvmStatic
      fun setBack(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setBack`(pressed)
      }

      @JvmStatic
      fun setLeft(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setLeft`(pressed)
      }

      @JvmStatic
      fun setRight(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setRight`(pressed)
      }

      @JvmStatic
      fun setSprint(input: ClientInput, pressed: Boolean) {
         getInputAccessor(input).`collective$setSprint`(pressed)
      }

      @JvmStatic
      fun setForwardMovement(input: ClientInput, forward: Float) {
         val movement = input.moveVector
         input.moveVector = Vec2(movement.x, forward)
      }

      @JvmStatic
      fun setSidewaysMovement(input: ClientInput, sideways: Float) {
         val movement = input.moveVector
         input.moveVector = Vec2(sideways, movement.y)
      }

      @JvmStatic
      fun clearKeyPresses(input: ClientInput) {
         setSneak(input, false)
         setSprint(input, false)
         setJump(input, false)
         setForward(input, false)
         setBack(input, false)
         setLeft(input, false)
         setRight(input, false)
      }

      @JvmStatic
      fun createInput(): ClientInput {
         val input = ClientInput()
         input.moveVector = Vec2(0.0f, 1.0f)
         return input
      }

      private fun getInputAccessor(input: ClientInput): InputAccessor {
         return input.keyPresses as InputAccessor
      }
   }
}
