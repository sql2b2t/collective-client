package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.IllegalDisconnectModule
import me.collective.client.modules.misc.AutoReconnectModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.Waypoint
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.MinecartTNT
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.function.Predicate

class AutoLogModule : Module("AutoLog", "Logs you out so you don't have to fight anyone.", ModuleCategory.COMBAT) {
    private val healthSetting: Setting<Int>
    private val invincTimeoutSetting: Setting<Float>
    private val reconnectSetting: Setting<Boolean>
    private val autoDisableSetting: Setting<Boolean>
    private val gearedOnlySetting: Setting<Boolean>
    private val unfocusedOnlySetting: Setting<Boolean>
    private val onRenderSetting: Setting<Boolean>
    private val crystalSetting: Setting<Boolean>
    private val tNTMinecartSetting: Setting<Boolean>
    val totemsSetting: Setting<Boolean>
    private val totemCountSetting: Setting<Int>
    private val elytraSetting: Setting<Boolean>
    private val durabilitySetting: Setting<Int>
    private val assumeInventorySetting: Setting<Boolean>
    val yLevelSetting: Setting<Boolean>
    private val ySetting: Setting<Float>
    private val waypointsSetting: Setting<Boolean>
    private val distanceSetting: Setting<Float>
    private var pendingDisconnect: Runnable? = null
    private val disconnectTimer: ElapsedTimer
    private var disconnectTimeMillis: Long = -1L

    init {
        this.healthSetting = this.add(NumberSetting("Health", 12, 1, 20).withUnit("hp"))
        this.invincTimeoutSetting = this.add(NumberSetting("InvincTimeout", 0.0f, 0.0f, 4.0f).withUnit("s"))
        this.reconnectSetting = this.add(BooleanSetting("Reconnect", false))
        this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", true))
        this.gearedOnlySetting = this.add(BooleanSetting("GearedOnly", false))
        this.unfocusedOnlySetting = this.add(BooleanSetting("UnfocusedOnly", false))
        this.onRenderSetting = this.add(BooleanSetting("OnRender", false))
        this.crystalSetting = this.add(BooleanSetting("Crystal", false))
        this.tNTMinecartSetting = this.add(BooleanSetting("TNTMinecart", false))
        this.totemsSetting = this.add(BooleanSetting("Totems", false).markVisibilityDependency())

        val totemCountVisibility = Predicate<Any?> { this.totemsSetting.isExpanded }
        this.totemCountSetting = this.add(NumberSetting("TotemCount", 1, 0, 16, totemCountVisibility))

        this.elytraSetting = this.add(BooleanSetting("Elytra", false).markVisibilityDependency())
        this.durabilitySetting = this.add(NumberSetting("Durability", 5, 1, 100).withUnit("%").dependsOn(this.elytraSetting))
        this.assumeInventorySetting = this.add(BooleanSetting("AssumeInventory", false).dependsOn(this.elytraSetting))
        this.yLevelSetting = this.add(BooleanSetting("YLevel", false).markVisibilityDependency())
        this.ySetting = this.add(NumberSetting("Y", 64.0f, -64.0f, 300.0f).dependsOn(this.yLevelSetting))
        this.waypointsSetting = this.add(BooleanSetting("Waypoints", false).markVisibilityDependency())
        this.distanceSetting = this.add(NumberSetting("Distance", 16.0f, 8.0f, 128.0f).withUnit("m").dependsOn(this.waypointsSetting))
        this.disconnectTimer = ElapsedTimer()
    }

    private fun requestDisconnect(reason: String) {
        if (this.autoDisableSetting.value) {
            this.disable()
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val connection = player.connection.connection
        if (connection != null) {
            this.disconnectTimeMillis = System.currentTimeMillis()

            if (!this.reconnectSetting.value) {
                ClientContext.serverStateManager.autoLogged = true
                autoReconnect.disable()
            }

            val ignoreProxySettingSource = illegalDisconnect.ignoreProxySetting
            val allowIllegalDisconnect = !ignoreProxySettingSource.value || !IllegalDisconnectModule.isIgnoredProxy()

            if (illegalDisconnect.isToggled && allowIllegalDisconnect) {
                if (this.pendingDisconnect == null) {
                    // Один запрос на дисконнект: раньше sendInvalidChatPacket уходил каждый тик
                    // (~30 пакетов), пока соединение не оборвётся.
                    IllegalDisconnectModule.sendInvalidChatPacket()
                    this.disconnectTimer.reset()
                }

                this.pendingDisconnect = Runnable {
                    connection.disconnect(this.buildDisconnectReason(reason))
                }
                return
            }

            connection.disconnect(this.buildDisconnectReason(reason))
        }
    }

    private fun buildDisconnectReason(reason: String): Component {
        val formatterSnapshot = SimpleDateFormat("HH:mm")
        val formattedTime = formatterSnapshot.format(Date(this.disconnectTimeMillis))
        val message = Component.empty()
        message.append(Component.literal("[AutoLog] ").withStyle(ChatFormatting.GRAY))
        message.append(Component.literal(reason).withStyle(ChatFormatting.RED))
        message.append("\n\n")
        message.append(Component.literal("Logged out at $formattedTime").withStyle(ChatFormatting.GRAY))
        return message
    }

    override fun getInfo(): String {
        return String.format("%d.0", this.healthSetting.value)
    }

    override fun onEnable() {
        this.pendingDisconnect = null
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        if (this.isWorldUnavailable) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        if (player.isSpectator || player.hasInfiniteMaterials()) {
            return
        }

        if (this.gearedOnlySetting.value) {
            if (!PlayerStateUtils.hasArmor(player)) {
                return
            }
        }

        if (this.elytraSetting.value) {
            if (this.isElytraDurabilityUnsafe()) {
                this.requestDisconnect("Your elytra durability is too low!")
                return
            }
        }

        if (this.yLevelSetting.value) {
            val currentY = player.y
            val heightLimit = this.ySetting.value
            if (currentY < heightLimit) {
                this.requestDisconnect("You are below Y level ${this.ySetting.value}")
                return
            }
        }

        val visibleWaypoints = ClientContext.waypointRegistry.visible
        if (this.waypointsSetting.value && visibleWaypoints.isNotEmpty()) {
            val firstWaypoint = visibleWaypoints.first() as Waypoint
            val waypointDistance = firstWaypoint.distanceTo(player.eyePosition)
            val distanceLimit = this.distanceSetting.value
            if (waypointDistance <= distanceLimit) {
                this.requestDisconnect("You are close to ${firstWaypoint.name} waypoint!")
                return
            }
        }

        val ticksSinceSpawn = player.tickCount.toFloat()
        val secondsSinceSpawn = ticksSinceSpawn / 20.0f
        val invincibilityTimeout = this.invincTimeoutSetting.value
        if (secondsSinceSpawn < invincibilityTimeout) {
            return
        }

        if (this.onRenderSetting.value) {
            for (renderedPlayer in level.players()) {
                if (renderedPlayer !is FakeplayerCommand.SimulatedPlayer) {
                    val renderedPlayerName = renderedPlayer.name.string
                    if (!ClientContext.socialRegistry.isFriendName(renderedPlayerName) && renderedPlayer != player) {
                        this.requestDisconnect("${renderedPlayer.name.string} has entered your render distance!")
                    }
                }
            }
        }

        val playerBounds = player.boundingBox
        val expandedMinecartBounds = playerBounds.inflate(8.0)
        if (!level.getEntitiesOfClass(MinecartTNT::class.java, expandedMinecartBounds) { it.isPrimed }.isEmpty()) {
            if (this.tNTMinecartSetting.value) {
                this.requestDisconnect("You are at risk of getting blown up by a TNT minecart!")
            }
        }

        if (this.crystalSetting.value) {
            for (entity in level.entitiesForRendering()) {
                if (entity is EndCrystal) {
                    val crystalDistanceSquared = player.distanceToSqr(entity)
                    if (crystalDistanceSquared <= 128.0) {
                        val explosionDamage = DamageUtils.getCrystalDamage(entity.position(), player)
                        if (explosionDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
                            this.requestDisconnect("You are at risk of getting blown up by an end crystal!")
                        }
                    }
                }
            }
        }

        val health = EntityUtils.getPlayerHealthWithAbsorption().toInt()
        if (player.isDeadOrDying || health == 0) {
            return
        }

        if (ClientAccess.collectiveMinecraft.isWindowActive) {
            if (this.unfocusedOnlySetting.value) {
                return
            }
        }

        var totemCount = player.inventory.items
            .filter { it.`is`(Items.TOTEM_OF_UNDYING) }
            .sumOf { it.count }

        totemCount += player.inventoryMenu.inputGridSlots
            .filter { this.containsTotem(it) }
            .sumOf { it.item.count }

        if (player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
            totemCount += player.offhandItem.count
        }

        val totemLimit = this.totemCountSetting.value
        var totemConditionMet = totemCount <= totemLimit
        if (!this.totemsSetting.value) {
            totemConditionMet = true
        }

        val healthLimit = this.healthSetting.value
        if (health <= healthLimit && totemConditionMet) {
            this.requestDisconnect("Your health is too low! ($health).")
        }
    }

    private fun containsTotem(slot: Slot): Boolean {
        val stackSnapshot = slot.item
        return stackSnapshot.`is`(Items.TOTEM_OF_UNDYING)
    }

    @EventHandler
    private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
        if (this.isWorldUnavailable) {
            this.pendingDisconnect = null
        }

        val disconnectAction = this.pendingDisconnect
        if (disconnectAction != null) {
            if (this.disconnectTimer.hasElapsed(1500L)) {
                disconnectAction.run()
                this.pendingDisconnect = null
            }
        }
    }

    private fun isElytraDurabilityUnsafe(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val chestIndex = EquipmentSlot.CHEST.getIndex(36)
        val equippedStack = player.inventory.getItem(chestIndex)

        if (equippedStack.`is`(Items.ELYTRA)) {
            if (equippedStack.isDamageableItem) {
                val equippedDurability = InventoryUtils.getDurabilityPercent(equippedStack)
                val durabilityLimit = this.durabilitySetting.value
                if (equippedDurability > durabilityLimit) {
                    return false
                }

                if (this.assumeInventorySetting.value) {
                    for (candidateStack in player.inventory.items) {
                        if (candidateStack.`is`(Items.ELYTRA)) {
                            if (!candidateStack.isDamageableItem) {
                                return false
                            }

                            val candidateDurability = InventoryUtils.getDurabilityPercent(candidateStack)
                            if (candidateDurability > durabilityLimit) {
                                return false
                            }
                        }
                    }
                }

                return true
            }
        }

        return false
    }

    @EventHandler
    private fun onPacketReceived(event: PacketEvent.Receive) {
        if (event.packet is ClientboundDisconnectPacket) {
            this.pendingDisconnect = null
        }
    }

    companion object {
        lateinit var illegalDisconnect: IllegalDisconnectModule
        lateinit var autoReconnect: AutoReconnectModule

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            illegalDisconnect = modules.getModule(IllegalDisconnectModule::class.java) as IllegalDisconnectModule
            autoReconnect = modules.getModule(AutoReconnectModule::class.java) as AutoReconnectModule
        }
    }
}
