package me.collective.client.render

import me.collective.client.ClientContext
import me.collective.client.modules.render.NoRenderModule

/**
 * Общее решение для миксинов над {@code GlyphInfo.getShadowOffset}:
 * ленивый доступ к NoRenderModule вместо lookup-а реестра на каждый глиф.
 */
object GlyphShadowOverride {
    private var noRenderModule: NoRenderModule? = null

    private fun noRenderModule(): NoRenderModule? {
        if (noRenderModule == null) {
            noRenderModule = ClientContext.getModule(NoRenderModule::class.java)
        }
        return noRenderModule
    }

    @JvmStatic
    fun isOverridden(): Boolean {
        val module = noRenderModule()
        return module != null &&
            module.isToggled() &&
            module.textShadowSetting.getValue()
    }
}
