package me.collective.client.managers

import net.minecraft.world.entity.EntityTypes
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.CrystalPlacementEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.misc.SwingModule
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.network.PacketSender
import me.collective.mixin.LivingEntityAccessor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.util.thread.BlockableEventLoop
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import java.util.Collections

class SwingInteractionManager : ClientAccess {
    private val pendingCrystals: MutableList<PendingCrystalPlacement> =
        Collections.synchronizedList(ArrayList(initialCrystalCapacity()))

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val isCrystalPlacementLimited: Boolean
        @JvmName("isCrystalPlacementLimitedProp")
        get() = isCrystalPlacementLimited()

    fun isCrystalPlacementLimited(): Boolean {
        val antiCheatSnapshot = antiCheat() ?: return false
        if (!antiCheatSnapshot.isPaperPlacement()) {
            return false
        }
        return pendingCrystals.size >= 9
    }

    fun tryTrackCrystalPlacement(position: BlockPos): Boolean {
        if (isCrystalPlacementLimited()) {
            return false
        }
        pendingCrystals.add(PendingCrystalPlacement(position, System.currentTimeMillis() + 300L))
        ClientAccess.eventBus.post(CrystalPlacementEvent(position))
        return true
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        val now = System.currentTimeMillis()
        pendingCrystals.removeIf { it.expiresAtMillis <= now }
    }

    @JvmOverloads
    fun swing(hand: InteractionHand, action: SwingAction, packetOnly: Boolean = false) {
        val antiCheatSnapshot = antiCheat()
        if (antiCheatSnapshot == null || !action.isEnabled(antiCheatSnapshot)) {
            if (!packetOnly) {
                swingClientSide(hand)
            }
        } else {
            if (packetOnly) {
                PacketSender.sendSwing(hand)
            } else {
                ClientAccess.collectiveMinecraft.player?.swing(hand)
            }
        }
    }

    fun swingPacket(hand: InteractionHand, action: SwingAction) {
        swing(hand, action, true)
    }

    fun swingClientSide(hand: InteractionHand) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.swinging) {
            val swingTime = player.swingTime
            val swingDuration = (player as LivingEntityAccessor).getSwingDuration()
            if (swingTime < swingDuration / 2 && swingTime >= 0) {
                return
            }
        }

        player.swingTime = -1
        player.swinging = true
        val moduleSnapshot = swingModule()
        if (moduleSnapshot != null && moduleSnapshot.isToggled) {
            val overrideHand = moduleSnapshot.handSetting.value.hand
            if (overrideHand != null) {
                player.swingingArm = overrideHand
                return
            }
        }

        player.swingingArm = hand
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet = event.packet as? ClientboundAddEntityPacket ?: return
        if (packet.type != EntityTypes.END_CRYSTAL) {
            return
        }
        val spawnBoundsSnapshot = packet.type.getSpawnAABB(packet.x, packet.y, packet.z)
        val crystalBounds = spawnBoundsSnapshot.inflate(-BoundingBoxUtils.blockCornerInset)
        val executor: BlockableEventLoop<*> = ClientAccess.collectiveMinecraft
        executor.execute {
            pendingCrystals.removeIf { pending ->
                val placementBounds = AABB(capturePlacementPosition(pending))
                crystalBounds.intersects(placementBounds)
            }
        }
    }

    private data class PendingCrystalPlacement(
        val position: BlockPos,
        val expiresAtMillis: Long
    )

    companion object {
        private var antiCheat: AntiCheatModule? = null
        private var swingModule: SwingModule? = null

        private fun initialCrystalCapacity(): Int = 10

        private fun antiCheat(): AntiCheatModule? {
            if (antiCheat == null) {
                antiCheat = ClientContext.getModule(AntiCheatModule::class.java)
            }
            return antiCheat
        }

        private fun swingModule(): SwingModule? {
            if (swingModule == null) {
                swingModule = ClientContext.getModule(SwingModule::class.java)
            }
            return swingModule
        }

        private fun capturePlacementPosition(pending: PendingCrystalPlacement): BlockPos = pending.position
    }
}
