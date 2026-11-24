package me.collective.client.modules.misc

import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

class AutoReconnectModule : Module("AutoReconnect", "Reconnects to the server automatically if you get kicked.", ModuleCategory.MISC) {
    @JvmField
    val minutesSetting: Setting<Int> = this.add(NumberSetting<Int>("Minutes", 0, 0, 15).withUnit("m"))

    @JvmField
    val secondsSetting: Setting<Float> = this.add(NumberSetting<Float>("Seconds", 5.0f, 0.0f, 59.0f).withUnit("s"))

    init {
        this.secondsSetting.setConfigName("Delay")
        this.setDrawn(false)
    }
}
