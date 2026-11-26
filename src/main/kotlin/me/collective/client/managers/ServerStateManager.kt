package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.exploit.FastLatencyModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Player
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ServerStateManager : ClientAccess {
    private val tickRateSamples = ArrayDeque<Float>(20)
    private val setbackTimerState = ElapsedTimer()
    private val teleportAckTimerState = ElapsedTimer()
    private val packetFlowTimer = ElapsedTimer()
    private val peakLatencyTimer = ElapsedTimer()

    private var latestTickRateState = 0f
    private var averageTickRate = 0f
    private var lastPacketMillis = 0L
    private var lastEntityPacketMillis = 0L
    private var lastTimeUpdateMillis = 0L
    private var teleportIdState = 0
    private var serverSprintingState = false
    private var serverSneakingState = false
    private var inEnd = false
    private var serverDataState: ServerData? = null
    private var footerLatencyMillis = 0
    private var peakLatencyMillisState = 0

    @JvmField
    var autoLogged = false

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val isServerSneaking: Boolean
        @JvmName("isServerSneakingProp")
        get() = serverSneakingState

    fun isServerSneaking(): Boolean {
        return serverSneakingState
    }

    val isServerSprinting: Boolean
        @JvmName("isServerSprintingProp")
        get() = serverSprintingState

    fun isServerSprinting(): Boolean {
        return serverSprintingState
    }

    val teleportId: Int
        @JvmName("getTeleportIdProp")
        get() = teleportIdState

    fun getTeleportId(): Int {
        return teleportIdState
    }

    val teleportAckTimer: ElapsedTimer
        @JvmName("getTeleportAckTimerProp")
        get() = teleportAckTimerState

    fun getTeleportAckTimer(): ElapsedTimer {
        return teleportAckTimerState
    }

    val tickRate: Float
        @JvmName("getTickRateProp")
        get() = averageTickRate

    fun getTickRate(): Float {
        return averageTickRate
    }

    val latestTickRate: Float
        @JvmName("getLatestTickRateProp")
        get() = latestTickRateState

    fun getLatestTickRate(): Float {
        return latestTickRateState
    }

    val setbackTimer: ElapsedTimer
        @JvmName("getSetbackTimerProp")
        get() = setbackTimerState

    fun getSetbackTimer(): ElapsedTimer {
        return setbackTimerState
    }

    val latencyMillis: Int
        @JvmName("getLatencyMillisProp")
        get() = getLatencyMillis()

    fun getLatencyMillis(): Int {
        val player = ClientAccess.collectiveMinecraft.player ?: return 0
        val fastLatencyModule = fastLatency()
        if (fastLatencyModule.isToggled) {
            return fastLatencyModule.getLatencyMillis()
        }
        val playerLatency = getPlayerLatencyMillis(player)
        return if (playerLatency == 0) footerLatencyMillis else playerLatency
    }

    val latencyTicks: Int
        @JvmName("getLatencyTicksProp")
        get() = getLatencyTicks()

    fun getLatencyTicks(): Int {
        return ceil(getLatencyMillis() / 50.0f).toInt()
    }

    val peakLatencyMillis: Int
        @JvmName("getPeakLatencyMillisProp")
        get() = peakLatencyMillisState

    fun getPeakLatencyMillis(): Int {
        return peakLatencyMillisState
    }

    val serverData: ServerData?
        @JvmName("getServerDataProp")
        get() = serverDataState

    fun getServerData(): ServerData? {
        return serverDataState
    }

    fun setServerData(data: ServerData?) {
        serverDataState = data
    }

    val tickRateScale: Float
        @JvmName("getTickRateScaleProp")
        get() = getTickRateScale()

    fun getTickRateScale(): Float {
        return max(0.1f, averageTickRate / 20.0f)
    }

    fun getLastPacketTime(): Long {
        return getLastPacketTime(PacketActivity.ANY)
    }

    fun getLastPacketTime(activity: PacketActivity): Long {
        return when (activity) {
            PacketActivity.ANY -> lastPacketMillis
            PacketActivity.ENTITY -> lastEntityPacketMillis
        }
    }

    fun getPlayerLatencyMillis(player: Player): Int {
        val connection = ClientAccess.collectiveMinecraft.player?.connection ?: return 0
        val playerInfo = connection.getPlayerInfo(player.gameProfile.id())
        return playerInfo?.latency ?: 0
    }

    fun hasElapsedSinceSetback(durationMillis: Long): Boolean {
        return setbackTimerState.hasElapsed(durationMillis)
    }

    fun hasStablePacketFlowFor(durationMillis: Long): Boolean {
        return packetFlowTimer.hasElapsed(durationMillis)
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        val packetAgeMillis = System.currentTimeMillis() - lastPacketMillis
        if (packetAgeMillis > 500L) {
            packetFlowTimer.reset()
        }

        val currentLatency = getLatencyMillis()
        if (peakLatencyTimer.hasElapsed(10.0, TimeUnit.SECONDS) || currentLatency > peakLatencyMillisState) {
            peakLatencyMillisState = currentLatency
            peakLatencyTimer.reset()
        }

        val dimension = WorldUtils.getDimension()
        inEnd = dimension.isEnd
    }

    @EventHandler(priority = 200)
    private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
        val packet = event.packet
        if (packet is ServerboundPlayerCommandPacket) {
            if (packet.action == Action.START_SPRINTING) {
                if (serverSprintingState) {
                    event.cancel()
                }
                serverSprintingState = true
            }
            if (packet.action == Action.STOP_SPRINTING) {
                if (!serverSprintingState) {
                    event.cancel()
                }
                serverSprintingState = false
            }
        }
        if (packet is ServerboundPlayerInputPacket) {
            serverSneakingState = packet.input().shift()
        }
        if (packet is ServerboundAcceptTeleportationPacket) {
            teleportAckTimerState.reset()
        }
    }

    @EventHandler
    private fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
        autoLogged = false
        footerLatencyMillis = 0
        peakLatencyMillisState = 0
        peakLatencyTimer.setTime(-1L)
    }

    private fun recordReceivedPacket(packet: Packet<*>) {
        if (packet is ClientboundMoveEntityPacket || packet is ClientboundRotateHeadPacket || inEnd) {
            lastEntityPacketMillis = System.currentTimeMillis()
        }
        lastPacketMillis = System.currentTimeMillis()
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val receivedPacket = event.packet
        recordReceivedPacket(receivedPacket)

        if (receivedPacket is ClientboundPlayerPositionPacket) {
            teleportIdState = receivedPacket.id()
            if (teleportAckTimerState.hasElapsed(50L)) {
                setbackTimerState.reset()
            }
            val sprintPlayer = ClientAccess.collectiveMinecraft.player
            if (sprintPlayer != null) {
                serverSprintingState = sprintPlayer.isSprinting
            }
        }

        if (receivedPacket is ClientboundSetTimePacket) {
            if (lastTimeUpdateMillis != 0L) {
                if (tickRateSamples.size > 20) {
                    tickRateSamples.poll()
                }
                val measuredTickRate = 20.0f * (1000.0f / (System.currentTimeMillis() - lastTimeUpdateMillis).toFloat())
                latestTickRateState = max(0.0f, min(20.0f, measuredTickRate))
                tickRateSamples.add(latestTickRateState)

                var rateSum = 0.0f
                for (sample in tickRateSamples) {
                    rateSum += max(0.0f, min(20.0f, sample))
                }
                if (tickRateSamples.isNotEmpty()) {
                    rateSum /= tickRateSamples.size
                }
                averageTickRate = if (rateSum.toDouble() > 19.95) 20.0f else rateSum
            }
            lastTimeUpdateMillis = System.currentTimeMillis()
        }

        if (receivedPacket is ClientboundBundlePacket) {
            for (subpacket in receivedPacket.subPackets()) {
                ClientAccess.eventBus.post(PacketEvent.Receive(subpacket))
            }
        }

        if (receivedPacket is ClientboundTabListPacket) {
            val connection = ClientAccess.collectiveMinecraft.player?.connection ?: return
            if (connection.serverData == null) {
                return
            }
            val footer = receivedPacket.footer().string
            val latencyMatcher = LATENCY_PATTERN.matcher(footer)
            if (latencyMatcher.find()) {
                val latencyText = latencyMatcher.group()
                try {
                    val parsed = latencyText.substring(0, latencyText.length - 5).trim()
                    footerLatencyMillis = parsed.toInt()
                } catch (_: Throwable) {
                }
            }
        }
    }

    enum class PacketActivity {
        ANY,
        ENTITY,
    }

    companion object {
        private var fastLatency: FastLatencyModule? = null
        private val LATENCY_PATTERN: Pattern = Pattern.compile("\\d+ ping")

        private fun fastLatency(): FastLatencyModule {
            if (fastLatency == null) {
                fastLatency = ClientContext.getModule(FastLatencyModule::class.java)
            }
            return fastLatency!!
        }

        @JvmStatic
        fun bindModules(registry: ModuleRegistry) {
            fastLatency = registry.getModule(FastLatencyModule::class.java)
        }
    }
}
