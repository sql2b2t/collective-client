package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.config.ModulePresetCodec
import me.collective.client.gui.GuiElement
import me.collective.client.gui.components.ModuleComponent
import me.collective.client.gui.components.SettingComponent
import me.collective.client.gui.panels.GamePanel
import me.collective.client.gui.panels.ModuleCategoryPanel
import me.collective.client.managers.ConfigurationManager
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.HUDModule
import me.collective.client.modules.client.UIModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.GuiScaleUtils
import me.collective.client.render.TextUtils
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.FloatAnimation
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributeView
import java.util.ArrayList
import java.util.Locale

open class ClickGuiScreen : ClientScreen(), ClientAccess {
    var editingSetting: SettingComponent<*>? = null
        @JvmName("getEditingSettingProp") get
        @JvmName("setEditingSettingProp") set
    private var secretSequenceIndex: Int = 0
    private var gamePanelUnlocked: Boolean = false
    private var searchCursorVisible: Boolean = false
    private val searchFocusAnimation: FloatAnimation = FloatAnimation()
    private val searchCursorTimer: ElapsedTimer = ElapsedTimer()

    @JvmField
    var searchBox: EditBox? = null

    private var searchCompletion: String = ""

    @JvmField
    var openModuleComponents: MutableList<ModuleComponent> = ArrayList()

    init {
        this.gamePanelUnlocked = ConfigurationManager.USER_HOME_MARKER_PATH.toFile().exists()
        var panelX = 10

        for (category in ModuleCategory.values()) {
            if (category != ModuleCategory.HUD) {
                val categoryPanel = ModuleCategoryPanel(category)
                categoryPanel.setX(panelX)
                this.panels.add(categoryPanel)
                panelX += categoryPanel.getWidth() + 3
            }
        }

        if (this.gamePanelUnlocked) {
            this.addGamePanel()
        }
    }

    private fun handleSecretSequence(keyCode: Int) {
        if (!this.gamePanelUnlocked && GAME_PANEL_UNLOCK_SEQUENCE[this.secretSequenceIndex] == keyCode) {
            this.secretSequenceIndex++
            if (this.secretSequenceIndex == GAME_PANEL_UNLOCK_SEQUENCE.size) {
                try {
                    val markerPath = ConfigurationManager.USER_HOME_MARKER_PATH
                    markerPath.toFile().createNewFile()
                    val dos = Files.getFileAttributeView(
                        markerPath,
                        DosFileAttributeView::class.java,
                        LinkOption.NOFOLLOW_LINKS
                    )
                    if (dos != null) {
                        try {
                            dos.setHidden(true)
                        } catch (_: UnsupportedOperationException) {
                        }
                    }
                } catch (_: IOException) {
                }

                this.gamePanelUnlocked = true
                this.secretSequenceIndex = 0
                this.addGamePanel()
                this.resetPanelPositions()
            }
        } else {
            this.secretSequenceIndex = 0
        }
    }

    private fun handleSearchKey(keyCode: Int) {
        val box = this.searchBox ?: return
        val searchText = box.value
        when (keyCode) {
            GLFW.GLFW_KEY_F -> {
                if (isControlDown()) {
                    if (box.isFocused) {
                        this.reset()
                    } else {
                        this.setInitialFocus(box)
                    }
                }
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                var foundModule = false
                for (panel in this.panels) {
                    for (childEntry in panel.getChildren()) {
                        val element = childEntry as GuiElement
                        if (element is ModuleComponent) {
                            val moduleName = element.module.name
                            val completedSearch = TextUtils.normalizeKeyboardLayout(
                                searchText + this.searchCompletion
                            )
                            if (moduleName.equals(completedSearch, ignoreCase = true)) {
                                element.setOpen(true)
                                element.startSearchHighlight()
                                foundModule = true
                                break
                            }
                        }
                    }
                    if (foundModule) {
                        break
                    }
                }
                this.reset()
            }
            GLFW.GLFW_KEY_TAB -> {
                if (box.isFocused) {
                    box.value = TextUtils.normalizeKeyboardLayout(searchText + this.searchCompletion)
                }
            }
            GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                if (box.isFocused && searchText.isNotEmpty()) {
                    box.value = if (isControlDown()) {
                        ""
                    } else {
                        searchText.substring(0, searchText.length - 1)
                    }
                }
            }
        }
    }

    private fun getSearchCursor(): String {
        if (this.searchCursorTimer.hasElapsed(500L)) {
            this.searchCursorVisible = !this.searchCursorVisible
            this.searchCursorTimer.reset()
        }

        return if (this.searchCursorVisible) "_" else ""
    }

    private fun handlePresetPaste(keyCode: Int) {
        if (isControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            val presetCodec = ClientContext.configurationManager.getModulePresetCodec()
            try {
                val decodedPreset = presetCodec.decode(
                    ClientAccess.collectiveMinecraft.keyboardHandler.clipboard
                )
                val presetScreen = ModuleImportConfirmationScreen(
                    ClientContext.getClickGui(),
                    decodedPreset.module,
                    decodedPreset.json
                )
                presetScreen.open()
            } catch (pasteException: Exception) {
                pasteException.printStackTrace()
            }
        }
    }

    open fun getSearchBox(): EditBox? = this.searchBox

    open fun getEditingSetting(): SettingComponent<*>? = this.editingSetting

    open fun setEditingSetting(component: SettingComponent<*>?) {
        this.editingSetting = component
    }

    override fun reset() {
        super.reset()
        this.setFocused(null)
        this.searchBox?.isFocused = false
        this.searchBox?.value = ""
        this.searchBox?.setSuggestion("")
    }

    override fun onFilesDrop(paths: List<Path>) {
        PresetImportScreen.openForDroppedFiles(this, paths)
    }

    override fun renderContents(
        graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float
    ) {
        val box = this.searchBox
        this.searchFocusAnimation.setTarget(box?.isFocused == true, 300L)
        this.drawSearchDimmer(poseStack)
        val fontHeight = FontRenderer.INSTANCE.getHeight()
        if (this.openModuleComponents.size >= 3) {
            this.openModuleComponents.first().setOpen(false)
        }

        this.openModuleComponents.removeIf { it.isClosed() }
        if (box != null && box.isFocused) {
            val fontRenderer = FontRenderer.INSTANCE
            val searchText = box.value
            val completionText = this.searchCompletion
            val searchTextWidth = fontRenderer.getWidth(searchText + completionText + "_")
            val completionFormatting = ChatFormatting.GRAY.toString()
            val completionSuffix = this.searchCompletion
            val cursor = this.getSearchCursor()
            val cursorFormatting = ChatFormatting.WHITE.toString()
            val searchLabel = box.value + completionFormatting + completionSuffix + cursorFormatting + cursor
            fontRenderer.drawWithShadow(
                poseStack,
                searchLabel,
                GuiScaleUtils.getScaledWidth() / 2.0f - searchTextWidth / 2.0f,
                GuiScaleUtils.getScaledHeight() / 2.0f - fontHeight / 2.0f,
                Color.white
            )
        } else {
            val searchHint = ClientContext.languageTable.getTranslationOrDefault("ui.Search", "Ctrl + F to activate search")
            var hintY = (GuiScaleUtils.getScaledHeight() - fontHeight - 1).toFloat()
            if (this.getParentScreen() is TitleScreen) {
                hintY -= (fontHeight + 1).toFloat()
            }

            val hintColor = ColorUtils.pulse(Color.GRAY, Color.WHITE, 2000.0, 250.0)
            val hudMargin = HUD?.getSafeX() ?: 0
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                searchHint,
                GuiScaleUtils.getScaledWidth() - FontRenderer.INSTANCE.getWidth(searchHint) - hudMargin,
                hintY - 1.0f,
                hintColor
            )
        }
    }

    private fun drawSearchDimmer(poseStack: PoseStack) {
        val dimmerOpacity = this.searchFocusAnimation.getValue() * 0.2f
        val dimmerColor = ARGB.color(dimmerOpacity, 0)
        GuiDrawUtils.fillRect(
            poseStack,
            0.0f,
            0.0f,
            ClientAccess.collectiveMinecraft.window.guiScaledWidth.toFloat(),
            ClientAccess.collectiveMinecraft.window.guiScaledHeight.toFloat(),
            dimmerColor
        )
    }

    override fun init() {
        super.init()
        this.searchCompletion = ""
        val box = EditBox(
            ClientAccess.collectiveMinecraft.font,
            7,
            -100,
            100,
            9,
            Component.literal("Search")
        )
        box.isVisible = true
        box.setEditable(true)
        box.setMaxLength(64)
        box.setResponder { searchText ->
            if (searchText.isNotEmpty()) {
                val modules = ClientContext.moduleRegistry.getRegistry() as List<*>
                for (moduleEntry in modules) {
                    val module = moduleEntry as Module
                    if (module !is HUDModule && module !is HudWidgetModule &&
                        module.name.lowercase(Locale.ROOT).startsWith(TextUtils.normalizeKeyboardLayout(searchText.lowercase(Locale.ROOT)))
                    ) {
                        val cursorPosition = box.cursorPosition
                        if (cursorPosition <= module.name.length) {
                            this.searchCompletion = module.name.substring(cursorPosition)
                        } else {
                            this.searchCompletion = ""
                        }
                        return@setResponder
                    }
                }
            }

            this.searchCompletion = ""
        }
        this.searchBox = box
        this.addRenderableWidget(box)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        super.keyPressed(event)
        val toggleKeybind = UIModule.INSTANCE.getKeybind()
        if (toggleKeybind.code() == event.key() && !toggleKeybind.mouse() &&
            this.escapeCloseAllowed && this.searchBox?.isFocused != true
        ) {
            this.closeWithTransition()
            return true
        } else {
            this.handleSecretSequence(event.key())
            this.handleSearchKey(event.key())
            this.handlePresetPaste(event.key())
            return true
        }
    }

    fun addGamePanel() {
        val gamePanel = GamePanel()
        this.panels.add(gamePanel)
        this.resetPanelPositions()
    }

    override fun closeWithTransition() {
        super.closeWithTransition()
        ClientAccess.executorService.submit {
            ClientContext.configurationManager.save()
        }
    }

    fun isGamePanelUnlocked(): Boolean = this.gamePanelUnlocked

    companion object {
        private val GAME_PANEL_UNLOCK_SEQUENCE = intArrayOf(
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_UP,
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
            GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_A
        )

        private val HUD: HUDModule?
            get() = ClientContext.getModule(HUDModule::class.java) as? HUDModule

        @JvmStatic
        fun isControlDown(): Boolean {
            val window = ClientAccess.collectiveMinecraft.window.handle()
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS
        }
    }
}
