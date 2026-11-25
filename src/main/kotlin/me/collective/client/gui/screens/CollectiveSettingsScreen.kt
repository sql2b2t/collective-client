package me.collective.client.gui.screens

import me.collective.client.ClientContext
import me.collective.client.modules.client.UIModule
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class CollectiveSettingsScreen(private val parent: Screen?) : Screen(Component.literal("Collective Client")) {

    override fun init() {
        val centerX = this.width / 2

        this.addRenderableWidget(
            Button.builder(this.languageLabel()) { button ->
                val uiModule = ClientContext.getModuleOrNull(UIModule::class.java) ?: return@builder
                val current = uiModule.languageSetting.value
                val choices = UIModule.LanguageChoice.values()
                val next = choices[(current.ordinal + 1) % choices.size]
                uiModule.languageSetting.value = next
                button.message = this.languageLabel()
            }
                .bounds(centerX - 155, this.height / 6 + 48, 310, 20)
                .build()
        )

        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { this.onClose() }
                .bounds(centerX - 100, this.height - 29, 200, 20)
                .build()
        )
    }

    private fun languageLabel(): Component {
        val uiModule = ClientContext.getModuleOrNull(UIModule::class.java)
        val current = uiModule?.languageSetting?.value?.name ?: "-"
        val pattern = this.localized("ui.SettingsLanguage", "Language: %s")
        return Component.literal(pattern.replace("%s", current))
    }

    private fun localized(key: String, fallback: String): String {
        val table = ClientContext.languageTable
        return table?.getTranslationOrDefault(key, fallback) ?: fallback
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 6 - 10, 16777215)
        graphics.drawCenteredString(
            this.font,
            Component.literal(this.localized("ui.SettingsHint", "Applies to module descriptions and UI hints")),
            this.width / 2,
            this.height / 6 + 20,
            11184810
        )
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun onClose() {
        this.minecraft?.setScreen(this.parent)
    }
}
