package me.collective.client.settings.options

import me.collective.client.render.ChamsColorProvider
import me.collective.client.modules.render.ChamsModule
import me.collective.client.settings.NamedEntry
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.world.entity.Entity

enum class ChamsStyle(private val displayName: String) : ChamsColorProvider, NamedEntry {
    BOTH("Both"),
    FILL("Fill") {
        override fun getColors(chams: ChamsModule): Array<Color> {
            return arrayOf(ChamsColorProvider.TRANSPARENT_COLOR, chams.fillSetting.getValue())
        }
    },
    LINE("Line") {
        override fun getColors(chams: ChamsModule): Array<Color> {
            val colors: Array<Color> = arrayOf(chams.outlineSetting.getValue(), ChamsColorProvider.TRANSPARENT_COLOR)
            return colors
        }
    },
    PLAIN("Plain") {
        override fun renderEntity(chams: ChamsModule, entity: Entity, poseStack: PoseStack) {
        }
    },
    OFF("Off");

    override fun getName(): String = displayName

    override fun isEnabled(): Boolean = this != OFF
}
