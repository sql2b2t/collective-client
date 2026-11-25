package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.utils.math.FloatBounds
import me.collective.client.render.HudVerticalAlignment
import me.collective.client.render.HudHorizontalAlignment
import me.collective.client.settings.NamedEntry

import net.minecraft.util.Mth

enum class HudAnchor(
    private val displayName: String,
    val verticalAlignment: HudVerticalAlignment,
    val horizontalAlignment: HudHorizontalAlignment,
    private val boundsFactory: (screenWidth: Int, screenHeight: Int) -> FloatBounds
) : ClientAccess, NamedEntry {
    TOP_LEFT(
        "top_left",
        HudVerticalAlignment.TOP,
        HudHorizontalAlignment.LEFT,
        { screenWidth, screenHeight -> FloatBounds.fromPositionAndSize(0.0F, 0.0F, 10.0F, 10.0F) }
    ),
    TOP_RIGHT(
        "top_right",
        HudVerticalAlignment.TOP,
        HudHorizontalAlignment.RIGHT,
        { screenWidth, screenHeight -> FloatBounds.fromPositionAndSize(screenWidth - 10.0F, 0.0F, 10.0F, 10.0F) }
    ),
    BOTTOM_LEFT(
        "bottom_left",
        HudVerticalAlignment.BOTTOM,
        HudHorizontalAlignment.LEFT,
        { screenWidth, screenHeight -> FloatBounds.fromPositionAndSize(0.0F, screenHeight - 10.0F, 10.0F, 10.0F) }
    ),
    BOTTOM_RIGHT(
        "bottom_right",
        HudVerticalAlignment.BOTTOM,
        HudHorizontalAlignment.RIGHT,
        { screenWidth, screenHeight ->
            FloatBounds.fromPositionAndSize(screenWidth - 10.0F, screenHeight - 10.0F, 10.0F, 10.0F)
        }
    ),
    TOP_CENTER(
        "top_center",
        HudVerticalAlignment.TOP,
        HudHorizontalAlignment.CENTER,
        { screenWidth, screenHeight ->
            FloatBounds.fromPositionAndSize(screenWidth / 2.0F - 10.0F, 0.0F, 10.0F, 10.0F)
        }
    ),
    NONE(
        "none",
        HudVerticalAlignment.FREE,
        HudHorizontalAlignment.FREE,
        { screenWidth, screenHeight ->
            FloatBounds.fromPositionAndSize(0.0F, 0.0F, screenWidth.toFloat(), screenHeight.toFloat())
        }
    );

    val bounds: FloatBounds @JvmName("boundsProp") get() = getBounds()

    override fun getName(): String = displayName

    fun contains(mouseX: Double, mouseY: Double): Boolean = getBounds().contains(
        Mth.clamp(
            mouseX, 0.0, ClientAccess.collectiveMinecraft.window.guiScaledWidth.toDouble()
        ),
        Mth.clamp(
            mouseY, 0.0, ClientAccess.collectiveMinecraft.window.guiScaledHeight.toDouble()
        )
    )

    fun getBounds(): FloatBounds = boundsFactory.invoke(
        ClientAccess.collectiveMinecraft.window.guiScaledWidth,
        ClientAccess.collectiveMinecraft.window.guiScaledHeight
    )
}
