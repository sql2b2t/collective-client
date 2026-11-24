package me.collective.client.events

import java.awt.Color
import net.minecraft.world.entity.Entity

class LivingEntityModelSubmitEvent private constructor() : CancellableEvent() {
    var entity: Entity? = null
    var lightCoords: Int = 0
    var red: Float = 0f
    var green: Float = 0f
    var blue: Float = 0f
    var alpha: Float = 0f

    fun setColor(color: Color) {
        red = color.red / 255.0f
        green = color.green / 255.0f
        blue = color.blue / 255.0f
        alpha = color.alpha / 255.0f
    }

    companion object {
        private val INSTANCE = LivingEntityModelSubmitEvent()

        @JvmStatic
        fun create(
            entity: Entity?,
            lightCoords: Int,
            red: Float,
            green: Float,
            blue: Float,
            alpha: Float
        ): LivingEntityModelSubmitEvent {
            INSTANCE.entity = entity
            INSTANCE.lightCoords = lightCoords
            INSTANCE.red = red
            INSTANCE.green = green
            INSTANCE.blue = blue
            INSTANCE.alpha = alpha
            INSTANCE.resetCancellation()
            return INSTANCE
        }
    }
}
