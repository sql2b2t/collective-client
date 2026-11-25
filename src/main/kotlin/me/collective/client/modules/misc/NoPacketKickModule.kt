package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

class NoPacketKickModule : Module("NoPacketKick", "Cancels broken packets that may cause you getting kicked.", ModuleCategory.MISC) {
    @JvmField
    val messageSetting: Setting<Boolean> = this.add(BooleanSetting("Message", false))

    init {
        this.setDrawn(false)
    }

    fun logInterceptedException(exception: Throwable) {
        if (this.messageSetting.getValue()) {
            try {
                val logger = ClientAccess.logger
                val message = exception.localizedMessage
                logger.warn("Intercepted exception: {}", message)
            } catch (ignored: Throwable) {
            }
        }
    }
}
