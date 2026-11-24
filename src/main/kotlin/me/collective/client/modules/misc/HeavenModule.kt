package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.events.CameraSetupEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.core.Direction.Axis
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

class HeavenModule : Module("Heaven", "Brings you to heaven after you die.", ModuleCategory.MISC) {
    private var deathTimeMillis: Long = 0

    @EventHandler
    private fun onCameraSetupEvent(event: CameraSetupEvent) {
        if (ClientAccess.collectiveMinecraft.screen !is DeathScreen) {
            return
        }
        val elapsedSinceDeath = System.currentTimeMillis() - this.deathTimeMillis
        val clampedElapsed = Mth.clamp(elapsedSinceDeath, 0L, 10000L).toFloat()
        val riseProgress = clampedElapsed / 10000.0f
        val capturedPosition = event.getPosition()
        val riseOffset = (riseProgress * 400.0f).toDouble()
        var position = capturedPosition.add(0.0, riseOffset, 0.0)
        val yAxis = Axis.Y
        val capturedY = position.y
        val heightLimit = 400.0
        val clampedY = Math.min(capturedY, heightLimit)
        position = position.with(yAxis, clampedY)
        event.setPosition(position)
        event.cancel()
    }

    @EventHandler
    private fun onScreenChange(event: ScreenChangeEvent.AfterInit) {
        if (event.newScreen is DeathScreen) {
            this.deathTimeMillis = System.currentTimeMillis()
        }
    }
}
