package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.render.ChamsModule
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

interface ChamsColorProvider : ClientAccess {
    fun applyColorOverride(colors: Array<Color>, overrideColor: Color) {
        val outlineColor = colors[0]
        val overrideOutline = ColorUtils.withAlpha(overrideColor, outlineColor.alpha)
        val fillColor = colors[1]
        EntityChamsRenderer.setColors(
            overrideOutline,
            ColorUtils.withAlpha(overrideColor, fillColor.alpha)
        )
    }

    fun renderEntity(module: ChamsModule, entity: Entity, matrices: PoseStack) {
        var colors = this.getColors(module)
        val fadeEnabled = module.fadeSetting.getValue()
        if (fadeEnabled) {
            val distanceFade = module.getDistanceFade(entity)
            val fadedColors = arrayOf(
                ColorUtils.withAlpha(colors[0], (distanceFade * colors[0].alpha).toInt()),
                ColorUtils.withAlpha(colors[1], (distanceFade * colors[1].alpha).toInt())
            )
            colors = fadedColors
        }

        EntityChamsRenderer.setColors(colors[0], colors[1])
        val friendsEnabled = module.friendsSetting.getValue()
        val colorsModule = ColorsBinding.module()
        if (friendsEnabled && colorsModule != null && entity is Player) {
            val socialRegistry = ClientContext.socialRegistry
            if (socialRegistry.isFriendPlayer(entity)) {
                val friendColor = colorsModule.friendColorSetting.getValue()
                this.applyColorOverride(colors, friendColor)
            }

            if (socialRegistry.isEnemyPlayer(entity)) {
                val enemyColor = colorsModule.enemyColorSetting.getValue()
                this.applyColorOverride(colors, enemyColor)
            }
        }

        if (entity is Player && module.usesStaticPlayerAnimation()) {
            entity.walkAnimation.speedOld = 0.0f
            entity.walkAnimation.speed = 0.0f
            entity.walkAnimation.position = 0.0f
        }

        EntityChamsRenderer.renderEntity(matrices, entity)
    }

    fun getColors(module: ChamsModule): Array<Color> {
        return arrayOf(
            module.outlineSetting.getValue(),
            module.fillSetting.getValue()
        )
    }

    fun isEnabled(): Boolean = false

    companion object {
        @JvmField
        val TRANSPARENT_COLOR: Color = ColorsBinding.createTransparentColor()

        @JvmStatic
        fun bindModuleRegistry(registry: ModuleRegistry) {
            ColorsBinding.module = registry.getModule(ColorsModule::class.java)
        }
    }

    private object ColorsBinding {
        var module: ColorsModule? = null

        fun module(): ColorsModule? {
            if (module == null) {
                module = ClientContext.getModule(ColorsModule::class.java)
            }
            return module
        }

        fun createTransparentColor(): Color = Color(0, 0, 0, 0)
    }
}
