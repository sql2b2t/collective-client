package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.gui.components.PresetListPanel
import me.collective.client.gui.components.PresetPanel
import me.collective.client.render.FontRenderer
import me.collective.client.render.RenderUtils
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.FloatAnimation
import net.minecraft.client.gui.GuiGraphics
import java.awt.Color
import java.nio.file.Path

class PresetScreen : ClientScreen() {
    @JvmField val listPanel: PresetListPanel = PresetListPanel(this)
    @JvmField val presetPanel: PresetPanel = PresetPanel(this)
    var selectedCategory: PresetCategory = PresetCategory.MODULES
        @JvmName("getSelectedCategoryProp") get
        @JvmName("setSelectedCategoryProp") set
    private val fileDropHintAnimation: FloatAnimation = FloatAnimation()

    init {
        this.panels.add(this.listPanel)
        this.presetPanel.x = this.listPanel.x + this.listPanel.width + 3
        this.panels.add(this.presetPanel)
    }

    override fun renderContents(graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.fileDropHintAnimation.setTarget(!ClientAccess.collectiveMinecraft.isWindowActive, 250L)
        val hintOpacity = this.fileDropHintAnimation.value
        if (hintOpacity != 0.0f) {
            RenderUtils.setColorAlpha(0.75f * hintOpacity)
            val hintText = "Drop preset files here"
            val hintWidth = FontRenderer.INSTANCE.getWidth(hintText)
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                hintText,
                (ClientAccess.collectiveMinecraft.window.guiScaledWidth - hintWidth) / 2.0f,
                ClientAccess.collectiveMinecraft.window.guiScaledHeight / 2.0f,
                Color.WHITE
            )
            RenderUtils.setColorAlpha(1.0f)
        }
    }

    override fun onFilesDrop(files: List<Path>) {
        PresetImportScreen.openForDroppedFiles(this, files)
    }

    fun getListPanel(): PresetListPanel = listPanel

    fun getPresetPanel(): PresetPanel = presetPanel

    fun getSelectedCategory(): PresetCategory = selectedCategory

    fun setSelectedCategory(category: PresetCategory) {
        this.selectedCategory = category
    }
}
