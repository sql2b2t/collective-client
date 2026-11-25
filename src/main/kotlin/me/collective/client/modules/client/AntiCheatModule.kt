package me.collective.client.modules.client

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.integrations.ProtocolCompatibility
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PermanentModule
import me.collective.client.modules.movement.VelocityModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AntiCheatMiningChoice
import me.collective.client.settings.options.AntiCheatPlacementsChoice
import me.collective.client.settings.options.AntiCheatRotationsChoice
import me.collective.client.settings.options.AntiCheatStrictDirectionChoice

import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.BundleContents
import net.minecraft.world.item.component.ItemContainerContents

class AntiCheatModule : PermanentModule("AntiCheat", "Manages the client's behavior on different anti-cheats.", ModuleCategory.CLIENT) {
    @JvmField
    val value2b2tSetting: Setting<Boolean>
    private val limiterSetting: Setting<Boolean>
    @JvmField
    val noServerSlotSetting: Setting<Boolean>
    @JvmField
    val strictDirectionSetting: Setting<AntiCheatStrictDirectionChoice>
    @JvmField
    val miningModeSetting: Setting<AntiCheatMiningChoice>
    @JvmField
    val placementsSetting: Setting<AntiCheatPlacementsChoice>
    @JvmField
    val movementSyncSetting: Setting<Boolean>
    @JvmField
    val rotationsSetting: Setting<AntiCheatRotationsChoice>
    @JvmField
    val swingSetting: Setting<Boolean>
    @JvmField
    val placeSetting: Setting<Boolean>
    @JvmField
    val hitSetting: Setting<Boolean>
    @JvmField
    val miningSwingSetting: Setting<Boolean>
    @JvmField
    val assumeBlockBreakingSetting: Setting<Boolean>
    private val movementHandler: AntiCheatMovementHandler
    private val packetRateLimiter: PacketRateLimiter
    private var miningStartedWithoutGrimStrict: Boolean = false
    private var silentRotationWarningShown: Boolean = false
    private var wasFallFlying: Boolean = false
    private var sneakResyncTicks: Int = 0
    // Java-поле было protected; AntiCheatMovementHandler.kt (тот же пакет) читает напрямую.
    @JvmField
    var movementYawOffsetEnabled: Boolean = false

    val is2b2tEnabled: Boolean @JvmName("is2b2tEnabledProp") get() = is2b2tEnabled()
    val isMovementSyncEnabled: Boolean @JvmName("isMovementSyncEnabledProp") get() = isMovementSyncEnabled()
    val isPaperPlacement: Boolean @JvmName("isPaperPlacementProp") get() = isPaperPlacement()

    fun is2b2tEnabled(): Boolean {
        return this.value2b2tSetting.value
    }

    fun isMovementSyncEnabled(): Boolean {
        return this.movementSyncSetting.value
    }

    fun isPacketLimiterActive(): Boolean {
        return this.is2b2tEnabled() &&
         this.isMovementSyncEnabled() &&
         this.limiterSetting.value
    }

    fun getRecentPacketCount(): Int {
        val limiter = this.packetRateLimiter
        val globalLimiter = this.packetRateLimiter
        return limiter.countRecentPackets(globalLimiter.globalWindow)
    }

    fun getPacketLimit(): Int {
        return this.packetRateLimiter.globalWindow.limit
    }

    fun getMovementHandler(): AntiCheatMovementHandler {
        return this.movementHandler
    }

    fun isPaperPlacement(): Boolean {
        return this.placementsSetting.value == AntiCheatPlacementsChoice.PAPER
    }

    fun isPacketLimitReached(): Boolean {
        if (this.isPacketLimiterActive()) {
            val limiter = this.packetRateLimiter
            val globalLimiter = this.packetRateLimiter
            return limiter.isLimitReached(globalLimiter.globalWindow)
        }
        return false
    }

    private fun shouldResyncSneakingAfterFlight(): Boolean {
        if (!this.movementSyncSetting.value || this.is2b2tEnabled()) {
            return false
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player.isShiftKeyDown) {
            return false
        }
        return this.wasFallFlying && !PlayerStateUtils.isFallFlying()
    }

    private fun warnGrimStrictRequiresReconnect() {
        if (!this.isWorldUnavailable()) {
            if (this.is2b2tEnabled()) {
                val miningMode: Setting<AntiCheatMiningChoice> = this.miningModeSetting
                if (miningMode.value == AntiCheatMiningChoice.GRIM_STRICT) {
                    if (!this.miningStartedWithoutGrimStrict || ClientAccess.collectiveMinecraft.isLocalServer) {
                        return
                    }

                    val message = Component.literal(("To use GrimStrict mining you must reconnect to the server.\n"))
                    val signature = ChatUtils.DEFAULT_SIGNATURE
                    val priority = ChatUtils.MessagePriority.HIGH
                    ChatUtils.printChat(message, signature, priority)
                    return
                }
            }
        }
    }

    private fun warnSilentRotationsCompatibility() {
        if (this.rotationsSetting.value != AntiCheatRotationsChoice.SILENT ||
         !this.movementSyncSetting.value ||
         ClientAccess.collectiveMinecraft.isLocalServer
        ) {
            return
        }
        if (ProtocolCompatibility.getTargetVersion().olderEq_1_20_6() || this.silentRotationWarningShown) {
            return
        }
        val message = ClientContext.languageTable.getTranslationOrDefault(
            "anticheat.SilentRotationsWarning",
            "Rotations mode \"Silent\" works only when you have ViaFabric(Plus) set to version 1.17 - 1.20.6."
        )
        ChatUtils.printChat(Component.literal(message), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.HIGH)
        this.silentRotationWarningShown = true
    }

    fun shouldCancelPacket(packet: Packet<*>): Boolean {
        return this.packetRateLimiter.shouldCancelPacket(packet)
    }

    private fun reverseBundleContents(stack: ItemStack?) {
        if (stack != null) {
            if (!stack.isEmpty) {
                val bundleType: DataComponentType<BundleContents> = DataComponents.BUNDLE_CONTENTS
                val bundleContents = stack.get(bundleType)
                val containerType: DataComponentType<ItemContainerContents> = DataComponents.CONTAINER
                val containerContents = stack.get(containerType)
                if (bundleContents != null) {
                    val items = bundleContents.items
                    val action = Consumer<ItemStack> { itemStack -> this.reverseBundleContents(itemStack) }
                    items.forEach(action)
                    val itemCopies = bundleContents.itemCopyStream()
                    val copiedItems = itemCopies.toList()
                    val reversedItems = copiedItems.reversed()
                    val replacementType: DataComponentType<BundleContents> = DataComponents.BUNDLE_CONTENTS
                    val replacement = BundleContents(reversedItems)
                    stack.set(replacementType, replacement)
                } else if (containerContents != null) {
                    val items = containerContents.items
                    val action = Consumer<ItemStack> { itemStack -> this.reverseBundleContents(itemStack) }
                    items.forEach(action)
                }

                return
            }
        }
    }

    @EventHandler
    private fun onLocalPlayerTick(event: LocalPlayerPreTickEvent) {
        if (this.shouldResyncSneakingAfterFlight()) {
            this.sneakResyncTicks = 3
        }

        if (this.sneakResyncTicks > 0) {
            this.sneakResyncTicks--
            if (this.sneakResyncTicks == 0) {
                val playerStateSnapshot = ClientContext.playerStateTracker

                playerStateSnapshot.setSneaking(true)
                playerStateSnapshot.setSneaking(false)
            }
        }

        this.wasFallFlying = PlayerStateUtils.isFallFlying()
    }

    private fun handleMiningStartPacket(startPacket: ServerboundPlayerActionPacket) {
        val miningMode: Setting<AntiCheatMiningChoice> = this.miningModeSetting
        if (miningMode.value == AntiCheatMiningChoice.GRIM_STRICT) {
            val sequence = PacketSender.nextPredictionSequence()
            val stopPacket = ServerboundPlayerActionPacket(
                Action.STOP_DESTROY_BLOCK, sourcePosition(startPacket), sourceDirection(startPacket), sequence
            )
            PacketSender.markImportant(startPacket)
            PacketSender.markImportant(stopPacket)
            PacketSender.send(stopPacket)
        } else {
            this.miningStartedWithoutGrimStrict = true
        }
    }

    @EventHandler(priority = -100)
    private fun onMiningPacketSend(
        event: PacketEvent.Send
    ) {
        val actionPacket = event.packet
        if (actionPacket is ServerboundPlayerActionPacket) {
            if (!event.isCancelled) {
                if (actionPacket.action == Action.START_DESTROY_BLOCK) {
                    this.handleMiningStartPacket(actionPacket)
                }

                if (actionPacket.action == Action.STOP_DESTROY_BLOCK) {
                    val miningMode: Setting<AntiCheatMiningChoice> = this.miningModeSetting
                    if (miningMode.value == AntiCheatMiningChoice.GRIM) {
                        val randomSnapshot = ThreadLocalRandom.current()

                        val randomOffset = randomSnapshot.nextInt((50))

                        val offset = randomOffset + 500
                        val action = Action.ABORT_DESTROY_BLOCK
                        val startPosition = actionPacket.pos
                        val offsetPosition = startPosition.offset(0, offset, 0)
                        val abortDirection = actionPacket.direction
                        PacketSender.sendPlayerAction(action, offsetPosition, abortDirection)
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onConnect(event: ConnectScreenConnectEvent) {
        this.miningStartedWithoutGrimStrict = false
        this.silentRotationWarningShown = false
        ClientContext.taskScheduler.scheduleRotationTask({ this.warnSilentRotationsCompatibility() }, 5)
    }

    @EventHandler(priority = 100)
    private fun onPacketSend(
        event: PacketEvent.Send
    ) {
        val actionPacket = event.packet
        if (actionPacket is ServerboundPlayerActionPacket) {
            if (actionPacket.action == Action.START_DESTROY_BLOCK) {
                val miningMode: Setting<AntiCheatMiningChoice> = this.miningModeSetting
                if (miningMode.value == AntiCheatMiningChoice.GRIM_STRICT) {
                    val importantPacket: Packet<*> = event.packet
                    PacketSender.markImportant(importantPacket)
                }
            }
        }
        if (this.is2b2tEnabled()) {
            val packetLimiterSetting: Setting<Boolean> = this.limiterSetting
            val limiterEnabled = packetLimiterSetting.value
            if (limiterEnabled) {
                val limiterSnapshot = this.packetRateLimiter
                val packet = event.packet
                if (limiterSnapshot.shouldCancelPacket(packet)) {
                    event.cancel()
                }
                return
            }
        }
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        if (!this.is2b2tEnabled()) {
            return
        }
        val packet = event.packet
        if (packet is ClientboundContainerClosePacket) {
            if (packet.containerId == 0) {
                event.cancel()
            }
        }

        if (packet is ClientboundContainerSetContentPacket) {
            val inventoryItems = packet.items
            val action = Consumer<ItemStack> { itemStack -> this.reverseBundleContents(itemStack) }
            inventoryItems.forEach(action)
            val carriedItem = packet.carriedItem
            this.reverseBundleContents(carriedItem)
        }

        if (packet is ClientboundContainerSetSlotPacket) {
            val slotItem = packet.item
            this.reverseBundleContents(slotItem)
        }

        if (packet is ClientboundSetPlayerInventoryPacket) {
            try {
                packet.slot()
            } catch (slotMatchFailure: Throwable) {
                throw MatchException(slotMatchFailure.toString(), slotMatchFailure)
            }

            val inventoryContents: ItemStack
            try {
                inventoryContents = packet.contents()
            } catch (inventoryContentsMatchFailure: Throwable) {
                throw MatchException(inventoryContentsMatchFailure.toString(), inventoryContentsMatchFailure)
            }
            this.reverseBundleContents(inventoryContents)
        }

        if (packet is ClientboundSetCursorItemPacket) {
            val cursorContents: ItemStack
            try {
                cursorContents = packet.contents()
            } catch (cursorMatchFailure: Throwable) {
                throw MatchException(cursorMatchFailure.toString(), cursorMatchFailure)
            }
            this.reverseBundleContents(cursorContents)
        }
    }

    init {
        this.value2b2tSetting = this.add(BooleanSetting("2b2t", false))
        val limiterVisibility = Predicate<Boolean> { ignored -> this.value2b2tSetting.value }
        this.limiterSetting = this.add(BooleanSetting("Limiter", false, limiterVisibility))
        this.noServerSlotSetting = this.add(BooleanSetting("NoServerSlot", true))
        this.strictDirectionSetting = this.add(EnumSetting("StrictDirection", AntiCheatStrictDirectionChoice.NCP))
        this.miningModeSetting = this.add(EnumSetting("Mining", AntiCheatMiningChoice.VANILLA))
        this.placementsSetting = this.add(EnumSetting("Placements", AntiCheatPlacementsChoice.VANILLA))
        this.movementSyncSetting = this.add(BooleanSetting("MovementSync", false).markVisibilityDependency())
        val rotationsDefault = AntiCheatRotationsChoice.SILENT
        val rotationsOption = EnumSetting(("Rotations"), rotationsDefault)
        this.rotationsSetting = this.add(rotationsOption.dependsOn(this.movementSyncSetting))
        this.swingSetting = this.add(BooleanSetting("Swing", true).markVisibilityDependency())
        this.placeSetting = this.add(BooleanSetting("Place", true).dependsOn(this.swingSetting))
        this.hitSetting = this.add(BooleanSetting("Hit", true).dependsOn(this.swingSetting))
        this.miningSwingSetting = this.add(BooleanSetting("Mining", false).dependsOn(this.swingSetting))
        val assumeBreakingVisibility = Predicate<Boolean> { ignored -> false }
        this.assumeBlockBreakingSetting = this.add(BooleanSetting("AssumeBlockBreaking", true, assumeBreakingVisibility))
        this.movementHandler = AntiCheatMovementHandler(this)
        this.packetRateLimiter = PacketRateLimiter()
        val eventBus = ClientAccess.eventBus
        eventBus.subscribe(this)
        this.rotationsSetting.setChangeCallback { this.warnSilentRotationsCompatibility() }
        this.movementSyncSetting.setChangeCallback { this.warnSilentRotationsCompatibility() }
        val callbackSetting: Setting<AntiCheatMiningChoice> = this.miningModeSetting
        val callback = Runnable { this.warnGrimStrictRequiresReconnect() }
        callbackSetting.setChangeCallback(callback)
        this.miningSwingSetting.setConfigName(("MiningSwing"))
    }

    companion object {
        @JvmField
        var speedMineModule: SpeedMineModule? = null

        @JvmField
        var velocityModule: VelocityModule? = null

        @JvmField
        val ROTATION_JITTER_STEP: Float

        init {
            val exponent = 3.0
            val base = 0.20000000298023224
            val basePower = Math.pow(base, exponent)

            val scaledPower = basePower * 8.0

            val rotationStep = scaledPower * 0.15

            ROTATION_JITTER_STEP = (rotationStep - 0.001).toFloat()
        }

        private fun sourcePosition(packet: ServerboundPlayerActionPacket): BlockPos {
            return packet.pos
        }

        private fun sourceDirection(packet: ServerboundPlayerActionPacket): Direction {
            return packet.direction
        }

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            speedMineModule = modules.getModule(
                SpeedMineModule::class.java
            ) as SpeedMineModule
            velocityModule = modules.getModule(
                VelocityModule::class.java
            ) as VelocityModule
        }
    }
}
