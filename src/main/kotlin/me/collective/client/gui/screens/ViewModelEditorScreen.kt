package me.collective.client.gui.screens

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.modules.render.ViewModelModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiScaleUtils
import me.collective.client.render.RenderUtils
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.math.MathUtils
import net.minecraft.client.gui.GuiGraphics
import java.awt.Color
import java.util.LinkedList

class ViewModelEditorScreen(private val viewmodel: ViewModelModule) : ClientScreen() {
    private val undoHistory: LinkedList<ViewModelSnapshot> = LinkedList()
    private val scrollSnapshotTimer: ElapsedTimer = ElapsedTimer()
    private var editMode: ViewModelEditMode = ViewModelEditMode.NONE
    private var dragStartSnapshot: ViewModelSnapshot = ViewModelSnapshot.capture(viewmodel)
    private var dragStartMouseX: Double = 0.0
    private var dragStartMouseY: Double = 0.0
    private var editingOffhand: Boolean = false

    init {
        undoHistory.add(dragStartSnapshot)
    }

    override fun renderContents(
        graphics: GuiGraphics, poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float
    ) {
        val snapshot = dragStartSnapshot
        if (getEditMode() != ViewModelEditMode.NONE) {
            var horizontalDelta = (MathUtils.floorToDecimals(
                (dragStartMouseX - mouseX) * 0.004999999888241291, 2
            ) * (if (editingOffhand) -1 else 1)).toFloat()
            var verticalDelta = MathUtils.floorToDecimals(
                (dragStartMouseY - mouseY) * 0.004999999888241291, 2
            ).toFloat()

            if (KeyUtils.isShiftDown()) {
                if (Math.abs(horizontalDelta) > Math.abs(verticalDelta)) {
                    verticalDelta = 0.0f
                } else {
                    horizontalDelta = 0.0f
                }
            }

            when (getEditMode()) {
                ViewModelEditMode.TRANSLATE -> {
                    if (KeyUtils.isControlDown()) {
                        if (editingOffhand) {
                            viewmodel.offXSetting.value = snapshot.offX + horizontalDelta
                            viewmodel.offYSetting.value = snapshot.offY + verticalDelta
                        } else {
                            viewmodel.mainXSetting.value = snapshot.mainX + horizontalDelta
                            viewmodel.mainYSetting.value = snapshot.mainY + verticalDelta
                        }
                    } else {
                        viewmodel.offXSetting.value = snapshot.offX + horizontalDelta
                        viewmodel.offYSetting.value = snapshot.offY + verticalDelta
                        viewmodel.mainXSetting.value = snapshot.mainX + horizontalDelta
                        viewmodel.mainYSetting.value = snapshot.mainY + verticalDelta
                    }
                }
                ViewModelEditMode.SCALE -> {
                    if (KeyUtils.isShiftDown()) {
                        val maxDelta = Math.max(horizontalDelta, verticalDelta)
                        horizontalDelta = Math.abs(maxDelta) * Math.signum(horizontalDelta)
                        verticalDelta = Math.abs(maxDelta) * Math.signum(verticalDelta)
                    }

                    horizontalDelta = -horizontalDelta
                    if (KeyUtils.isControlDown()) {
                        if (editingOffhand) {
                            viewmodel.offScaleXSetting.value = snapshot.offScaleX + horizontalDelta
                            viewmodel.offScaleYSetting.value = snapshot.offScaleY + verticalDelta
                        } else {
                            viewmodel.mainScaleXSetting.value = snapshot.mainScaleX + horizontalDelta
                            viewmodel.mainScaleYSetting.value = snapshot.mainScaleY + verticalDelta
                        }
                    } else {
                        viewmodel.offScaleXSetting.value = snapshot.offScaleX + horizontalDelta
                        viewmodel.offScaleYSetting.value = snapshot.offScaleY + verticalDelta
                        viewmodel.mainScaleXSetting.value = snapshot.mainScaleX + horizontalDelta
                        viewmodel.mainScaleYSetting.value = snapshot.mainScaleY + verticalDelta
                    }
                }
                ViewModelEditMode.ROTATE -> {
                    horizontalDelta /= 0.005f
                    verticalDelta /= 0.005f
                    if (KeyUtils.isControlDown()) {
                        if (editingOffhand) {
                            viewmodel.offRotateXSetting.value = MathUtils.wrapAngle(snapshot.offRotateX + verticalDelta)
                            viewmodel.offRotateYSetting.value = MathUtils.wrapAngle(snapshot.offRotateY + horizontalDelta)
                        } else {
                            viewmodel.mainRotateXSetting.value = MathUtils.wrapAngle(snapshot.mainRotateX + verticalDelta)
                            viewmodel.mainRotateYSetting.value = MathUtils.wrapAngle(snapshot.mainRotateY + horizontalDelta)
                        }
                    } else {
                        viewmodel.offRotateXSetting.value = MathUtils.wrapAngle(snapshot.offRotateX + verticalDelta)
                        viewmodel.offRotateYSetting.value = MathUtils.wrapAngle(snapshot.offRotateY + horizontalDelta)
                        viewmodel.mainRotateXSetting.value = MathUtils.wrapAngle(snapshot.mainRotateX + verticalDelta)
                        viewmodel.mainRotateYSetting.value = MathUtils.wrapAngle(snapshot.mainRotateY + horizontalDelta)
                    }
                }
                else -> {}
            }
        }
    }

    override fun onMouseClick(mouseX: Double, mouseY: Double, button: Int) {
        setEditMode(editModeForButton(button))
        if (getEditMode() != ViewModelEditMode.NONE) {
            dragStartSnapshot = ViewModelSnapshot.capture(viewmodel)
            undoHistory.add(dragStartSnapshot)
            val window = ClientAccess.collectiveMinecraft.window
            val scale = GuiScaleUtils.calculateScaleMultiplier(guiScale)
            editingOffhand = mouseX * scale < window.guiScaledWidth / 2.0f
            dragStartMouseX = mouseX
            dragStartMouseY = mouseY
        }
    }

    override fun onMouseRelease(mouseX: Double, mouseY: Double, button: Int) {
        setEditMode(ViewModelEditMode.NONE)
    }

    override fun mouseScrolled(
        mouseX: Double, mouseY: Double, horizontalScroll: Double, verticalScroll: Double
    ): Boolean {
        if (verticalScroll > -1.0 && verticalScroll < 1.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll)
        }

        val window = ClientAccess.collectiveMinecraft.window
        val scale = GuiScaleUtils.calculateScaleMultiplier(guiScale)
        val overOffhand = mouseX * scale < window.guiScaledWidth / 2.0f
        val depthDelta = (verticalScroll * -0.10000000149011612).toFloat()

        if (scrollSnapshotTimer.hasElapsed(100L)) {
            undoHistory.add(ViewModelSnapshot.capture(viewmodel))
        }
        scrollSnapshotTimer.reset()

        if (KeyUtils.isShiftDown()) {
            val offRotateZ = MathUtils.wrapAngle(
                viewmodel.offRotateZSetting.value + verticalScroll.toFloat() * 10.0f
            )
            val mainRotateZ = MathUtils.wrapAngle(
                viewmodel.mainRotateZSetting.value + verticalScroll.toFloat() * 10.0f
            )
            if (KeyUtils.isControlDown()) {
                if (overOffhand) {
                    viewmodel.offRotateZSetting.value = offRotateZ
                } else {
                    viewmodel.mainRotateZSetting.value = mainRotateZ
                }
            } else {
                viewmodel.offRotateZSetting.value = offRotateZ
                viewmodel.mainRotateZSetting.value = mainRotateZ
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll)
        } else if (KeyUtils.isAltDown()) {
            adjustDepthSettings(viewmodel.mainScaleZSetting, viewmodel.offScaleZSetting, overOffhand, depthDelta)
            return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll)
        } else {
            adjustDepthSettings(viewmodel.mainZSetting, viewmodel.offZSetting, overOffhand, depthDelta)
            return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (KeyUtils.isControlDown() && keyCode == 90 && undoHistory.isNotEmpty()) {
            undoHistory.last.apply(viewmodel)
            if (undoHistory.size != 1) {
                undoHistory.removeLast()
            }
        }

        if (keyCode == 261) {
            dragStartSnapshot = ViewModelSnapshot.createDefaults()
            dragStartSnapshot.apply(viewmodel)
            undoHistory.add(dragStartSnapshot)
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackgroundOverlay(poseStack: PoseStack) {
        val window = ClientAccess.collectiveMinecraft.window
        val screenWidth = window.guiScaledWidth.toFloat()
        val screenHeight = window.screenHeight.toFloat()
        RenderUtils.INSTANCE.fillRect(
            poseStack,
            screenWidth / 2.0f - 1.0f,
            0.0f,
            screenWidth / 2.0f + 1.0f,
            48.0f,
            OVERLAY_COLOR
        )
        val instructions = listOf(
            "Left Click - Rotate",
            "Right Click - Move",
            "Middle Click - Scale",
            "Mouse Scroll - Move by Z",
            "Mouse Scroll + Shift - Rotate by Z",
            "Mouse Scroll + Alt - Scale by Z",
            "Hold Ctrl - Modify one",
            "Hold Shift - Lock axis",
            "Ctrl + Z - Undo",
            "Delete - Reset to defaults"
        )
        var textY = 2

        for (instruction in instructions) {
            val textWidth = FontRenderer.INSTANCE.getWidth(instruction)
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                instruction,
                (screenWidth - textWidth) / 2.0f,
                50.0f + textY,
                ColorUtils.withAlpha(Color.WHITE, 180)
            )
            textY += FontRenderer.INSTANCE.height + 1
        }

        RenderUtils.INSTANCE.fillRect(
            poseStack,
            screenWidth / 2.0f - 1.0f,
            (textY + 52).toFloat(),
            screenWidth / 2.0f + 1.0f,
            screenHeight,
            OVERLAY_COLOR
        )
    }

    fun getEditMode(): ViewModelEditMode = editMode

    fun setEditMode(mode: ViewModelEditMode) {
        this.editMode = mode
    }

    private fun editModeForButton(button: Int): ViewModelEditMode {
        return when (button) {
            0 -> ViewModelEditMode.ROTATE
            1 -> ViewModelEditMode.TRANSLATE
            2 -> ViewModelEditMode.SCALE
            else -> ViewModelEditMode.NONE
        }
    }

    private fun adjustDepthSettings(
        mainSetting: Setting<Float>, offSetting: Setting<Float>, overOffhand: Boolean, delta: Float
    ) {
        if (KeyUtils.isControlDown()) {
            if (overOffhand) {
                offSetting.value = offSetting.value + delta
            } else {
                mainSetting.value = mainSetting.value + delta
            }
        } else {
            mainSetting.value = mainSetting.value + delta
            offSetting.value = offSetting.value + delta
        }
    }

    companion object {
        private val OVERLAY_COLOR = Color(255, 255, 255, 100)
    }
}
