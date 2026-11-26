package me.collective.client.modules.client

import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PermanentModule
import me.collective.client.modules.render.SearchModule
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ColorsSchemeChoice

import java.awt.Color

class ColorsModule : PermanentModule("Colors", "Manages the client's color system.", ModuleCategory.CLIENT) {
    private val rainbowSpeedSetting: Setting<Float> = add(NumberSetting("RainbowSpeed", 1.0f, 0.1f, 10.0f))

    @JvmField
    val themeColorSetting: Setting<Color> = add(ColorSetting("ThemeColor", Color(-3697420, true)))

    @JvmField
    val friendColorSetting: Setting<Color> = add(ColorSetting("FriendColor", Color(-16711681, true)))

    @JvmField
    val enemyColorSetting: Setting<Color> = add(ColorSetting("EnemyColor", Color(-4980736, true)))

    @JvmField
    val schemeSetting: Setting<ColorsSchemeChoice> = add(EnumSetting("Scheme", ColorsSchemeChoice.NORMAL))

    fun getRainbowPeriod(): Float {
        return 1.0f / this.rainbowSpeedSetting.value
    }

    init {
        schemeSetting.setChangeCallback {
            val search = searchModule
            if (search!!.isToggled()) search.restartSearch()
        }
    }

    companion object {
        private var searchModule: SearchModule? = null

        @JvmStatic
        fun bindSearchModule(registry: ModuleRegistry) {
            searchModule = registry.getModule(SearchModule::class.java) as SearchModule
        }
    }
}
