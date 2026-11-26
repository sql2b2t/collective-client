package me.collective.client.utils.movement

import net.minecraft.world.phys.Vec3

interface EntityMotionAccess {
   fun getCameraYaw(): Float

   fun setCameraPitch(pitch: Float)

   fun getCameraPitch(): Float

   fun setCameraYaw(yaw: Float)

   fun `collective$getPrevVelocity`(): Vec3
}
