package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PlayerIsStayingOnGroundSurfaceEvent
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

class SafeWalkModule : Module("SafeWalk", "Helps not to fall from blocks if you're bad.", ModuleCategory.MOVEMENT) {
    private val sneakSetting: Setting<Boolean>
    private val delaySetting: Setting<Int>
    private val edgeDelayTimer: ElapsedTimer

    init {
        val sneak = BooleanSetting("Sneak", false)
        sneak.markVisibilityDependency()
        this.sneakSetting = this.add(sneak)

        val delay = NumberSetting<Int>("Delay", 0, 0, 1000)
        delay.withUnit("ms")
        this.delaySetting = this.add(delay.dependsOn(this.sneakSetting))
        this.edgeDelayTimer = ElapsedTimer()
    }

    override fun onDisable() {
        if (this.sneakSetting.value) ClientAccess.collectiveMinecraft.options.keyShift.setDown(false)
    }

    @EventHandler
    private fun onPlayerTick(event: LocalPlayerTickEvent) {
        if (!this.sneakSetting.value) return


        val below = BlockPos.containing(ClientAccess.collectiveMinecraft.player!!.position().add(0.0, -0.1f.toDouble(), 0.0))
        val block: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(below)
        if (this.shouldSneakAtEdge(block)) {
            this.edgeDelayTimer.reset()
            ClientContext.inputOverrides.requestSneak(true)
        } else if (!this.edgeDelayTimer.hasElapsed(this.delaySetting.value.toLong())) {
            ClientContext.inputOverrides.requestSneak(true)
        }
    }

    @EventHandler
    private fun onGroundSurfaceQuery(event: PlayerIsStayingOnGroundSurfaceEvent) {
        if (!ClientAccess.collectiveMinecraft.player!!.isSwimming &&
         !ClientAccess.collectiveMinecraft.player!!.isInLava() &&
         !ClientAccess.collectiveMinecraft.player!!.isInWater() &&
         !this.sneakSetting.value
        ) event.cancel()
    }

    private fun shouldSneakAtEdge(block: BlockState): Boolean {
        return EntityUtils.hasCollisionBelow(ClientAccess.collectiveMinecraft.player!!) &&
         !ClientAccess.collectiveMinecraft.player!!.isInWater() &&
         !ClientAccess.collectiveMinecraft.player!!.isInLava() &&
         this.isUnsupportedBlock(block)
    }

    private fun isUnsupportedBlock(block: BlockState): Boolean {

        return block.`is`(Blocks.AIR) || block.getCollisionShape(ClientAccess.collectiveMinecraft.level!!, BlockPos.ZERO).isEmpty()
    }
}
