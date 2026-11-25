package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.utils.network.InteractPacketUtils
import me.collective.client.utils.network.PacketSender
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class CriticalsModule : Module("Criticals", "Turns your hits into critical ones.", ModuleCategory.COMBAT) {
    private val modeSetting: Setting<ModeChoice> = add(EnumSetting("Mode", ModeChoice.PACKET))
    private val pauseOnMoveSetting: Setting<Boolean> = add(BooleanSetting("PauseOnMove", false))
    private val onlyFullSetting: Setting<Boolean> = add(BooleanSetting("OnlyFull", false))
    private val onlyWeaponSetting: Setting<Boolean> = add(BooleanSetting("OnlyWeapon", true))
    private var pendingPositionCorrection = false

    init {
        pauseOnMoveSetting.addVisibilityPredicate { modeSetting.value != ModeChoice.GRIMV3 }
        onlyFullSetting.addVisibilityPredicate { modeSetting.value == ModeChoice.GRIMV3 }
    }

    private fun isCriticalWeapon(stack: ItemStack): Boolean {
        if (stack.`is`(Items.MACE)) {
            return true
        }
        return ItemUtils.isSword(stack) || ItemUtils.isAxe(stack) || stack.`is`(ItemTags.PICKAXES)
    }

    private fun clampPitch(pitch: Float): Float {
        return pitch.coerceIn(-MathConstants.NINETY_DEGREES.toFloat(), MathConstants.NINETY_DEGREES.toFloat())
    }

    override fun getInfo(): String {
        return modeSetting.value.getName()
    }

    protected fun canPerformCritical(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false

        if (onlyWeaponSetting.value) {
            val heldStack = player.mainHandItem
            if (!isCriticalWeapon(heldStack)) {
                return false
            }
        }

        if (pauseOnMoveSetting.value || modeSetting.value == ModeChoice.GRIMV3) {
            if (MovementPhysics.isLocalPlayerMoving()) {
                return false
            }
        }

        return if (modeSetting.value == ModeChoice.GRIMV3) {
            if (onlyFullSetting.value) {
                val eyeY = player.eyeY
                if (!WallsVelocityHandler.hasPlayerCollisionAtHeight(eyeY)) {
                    return false
                }
            }
            player.onGround() && WallsVelocityHandler.hasUsableGroundCollision()
        } else {
            if (modeSetting.value == ModeChoice.GRIM && !player.onGround()) {
                return true
            }
            player.onGround() && player.verticalCollision && !player.isInLava && !player.isInWater
        }
    }

    val mode: ModeChoice @JvmName("getModeProp") get() = modeSetting.value
    fun getMode(): ModeChoice = modeSetting.value

    @EventHandler
    private fun onLocalPlayerPreTick(ignoredEvent: LocalPlayerPreTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        var rotations = floatArrayOf(player.yRot, player.xRot)

        val aura = auraModule
        if (aura != null && aura.isToggled && aura.target != null) {
            rotations = RotationManager.getRotationsToEntity(aura.target)
        }

        if (pendingPositionCorrection) {
            val x = player.x + 9.999999747378752e-05
            val y = player.y
            val z = player.z
            val yaw = rotations[0]
            var pitch = rotations[1]
            val pitchAdjustment = AntiCheatModule.ROTATION_JITTER_STEP
            pitch -= pitchAdjustment * 2.0f

            val clampedPitch = clampPitch(pitch)
            PacketSender.sendPositionRotation(x, y, z, yaw, clampedPitch, true)
            pendingPositionCorrection = false
        }
    }

    @EventHandler
    private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
        val packet = event.packet
        if (packet is ServerboundInteractPacket) {
            if (shouldApplyCritical(packet)) {
                val target = InteractPacketUtils.getEntity(packet) ?: return
                if (!event.isCancelled) {
                    performCritical()
                }
                ClientAccess.collectiveMinecraft.execute {
                    ClientAccess.collectiveMinecraft.player?.crit(target)
                }
            }
        }
    }

    protected fun performCritical() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        var rotations = floatArrayOf(player.yRot, player.xRot)

        val aura = auraModule
        if (aura != null && aura.isToggled && aura.target != null) {
            rotations = RotationManager.getRotationsToEntity(aura.target)
        }

        when (modeSetting.value) {
            ModeChoice.NCP -> {
                if (PlayerStateUtils.isInCobweb(player)) {
                    sendPositionOffsets(1.1e-7, 1.1e-8, 0.0)
                } else {
                    sendPositionOffsets(1.1e-7, 1.1e-8)
                }
            }
            ModeChoice.GRIM -> {
                val grimX = player.x
                val grimY = player.y - 1e-06
                val grimZ = player.z
                val grimYaw = rotations[0]
                val grimPitch = rotations[1]
                PacketSender.sendPositionRotation(grimX, grimY, grimZ, grimYaw, grimPitch, false)
            }
            ModeChoice.GRIMV3 -> {
                val firstX = player.x
                val firstY = player.y + 0.0626
                val firstZ = player.z
                val firstYaw = rotations[0]
                val firstPitch = rotations[1] + AntiCheatModule.ROTATION_JITTER_STEP
                val firstClampedPitch = clampPitch(firstPitch)
                PacketSender.sendPositionRotation(firstX, firstY, firstZ, firstYaw, firstClampedPitch, false)

                val secondX = player.x
                val secondY = player.y + 0.0455
                val secondZ = player.z
                val secondYaw = rotations[0]
                val secondPitch = rotations[1] - AntiCheatModule.ROTATION_JITTER_STEP
                val secondClampedPitch = clampPitch(secondPitch)
                PacketSender.sendPositionRotation(secondX, secondY, secondZ, secondYaw, secondClampedPitch, false)
                pendingPositionCorrection = true
            }
            ModeChoice.PACKET -> {
                sendPositionOffsets(0.0625101, 0.0)
            }
        }
    }

    private fun sendPositionOffsets(vararg offsets: Double) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        for (offset in offsets) {
            val x = player.x
            val y = player.y + offset
            val z = player.z
            PacketSender.sendPosition(x, y, z, false)
        }
    }

    private fun shouldApplyCritical(packet: ServerboundInteractPacket): Boolean {
        if (InteractPacketUtils.getAction(packet) == InteractPacketUtils.Action.ATTACK) {
            val aura = auraModule
            if (aura != null && aura.isToggled && aura.shouldSuppressCriticals()) {
                return false
            }
            if (canPerformCritical()) {
                val target = InteractPacketUtils.getEntity(packet)
                if (aura != null && aura.isToggled && aura.criticalAttackFlag) {
                    if (modeSetting.value != ModeChoice.GRIM) {
                        return false
                    }
                }
                if (target != null && target.isAlive && target !is EndCrystal) {
                    return true
                }
            }
        }
        return false
    }

    enum class ModeChoice(private val displayName: String) : NamedEntry {
        PACKET("Packet"),
        NCP("NCP"),
        GRIM("Grim"),
        GRIMV3("2b2t");

        override fun getName(): String = displayName
    }

    companion object {
        private var auraModule: AuraModule? = null

        init {
            auraModule = ClientContext.getModuleOrNull(AuraModule::class.java)
        }

        @JvmStatic
        fun bindModules(registry: ModuleRegistry) {
            auraModule = registry.getModule(AuraModule::class.java) as? AuraModule
        }
    }
}
