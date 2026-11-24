package me.collective.client.utils.combat

data class RotationRequest(
   @get:JvmName("angles") val angles: FloatArray,
   @get:JvmName("priority") val priority: Int
)
