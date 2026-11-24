package me.collective.client.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.InputPressEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.InteractPacketUtils
import me.collective.client.utils.network.PacketSender
import me.collective.mixin.ServerboundInteractPacketAccessor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

class AttributeSwapModule : Module(
    "AttributeSwap",
    "Swaps attributes of the main hand item with the target slot on attack.",
    ModuleCategory.COMBAT
) {
    private val autoLungeSetting: Setting<Boolean> = add(
        BooleanSetting("AutoLunge", false)
    )
    private val continuousSetting: Setting<Boolean> = add(
        BooleanSetting("Continuous", true)
    )
    private val threeDFlightSetting: Setting<Boolean> = add(
        BooleanSetting("3DFlight", true)
    )
    private val shieldBreakerSetting: Setting<Boolean> = add(
        BooleanSetting("ShieldBreaker", false)
    )
    private val noSwapSetting: Setting<Boolean> = add(
        BooleanSetting("NoSwapOnShieldBreak", true)
    )
    private val targetSlotSetting: Setting<Int> = add(
        NumberSetting("TargetSlot", 1, 1, 9)
    )
    private val swapBackSetting: Setting<Boolean> = add(
        BooleanSetting("SwapBack", true)
    )
    private val delaySetting: Setting<Int> = add(
        NumberSetting("Delay", 0, 0, 20).withUnit("t")
    )

    private var prevSlot = -1
    private var dDelay = 0
    private var didSwap = false
    private var isSwapping = false

    init {
        shieldBreakerSetting.addVisibilityPredicate { !autoLungeSetting.value }
        noSwapSetting.addVisibilityPredicate { !autoLungeSetting.value && shieldBreakerSetting.value }
        targetSlotSetting.addVisibilityPredicate {
            !autoLungeSetting.value && !(shieldBreakerSetting.value && noSwapSetting.value)
        }
        continuousSetting.addVisibilityPredicate { autoLungeSetting.value }
        threeDFlightSetting.addVisibilityPredicate { autoLungeSetting.value }
        delaySetting.addVisibilityPredicate { swapBackSetting.value }
    }

    override fun getInfo(): String {
        return if (autoLungeSetting.value) {
            if (continuousSetting.value) "LungeFly" else "Lunge"
        } else if (shieldBreakerSetting.value) {
            "ShieldBreaker"
        } else {
            "Slot ${targetSlotSetting.value}"
        }
    }

    override fun onDisable() {
        if (prevSlot != -1 && ClientAccess.collectiveMinecraft.player != null) {
            InventoryUtils.selectHotbarSlot(prevSlot)
        }
        prevSlot = -1
        dDelay = 0
        didSwap = false
        isSwapping = false
    }

    @EventHandler
    private fun onInputPress(event: InputPressEvent) {
        if (ClientAccess.collectiveMinecraft.screen != null) return
        if (!autoLungeSetting.value) return

        val attackKey = ClientAccess.collectiveMinecraft.options.keyAttack.key
        val isMouse = attackKey.type == InputConstants.Type.MOUSE
        val isAttackKey = (event.isMouse == isMouse && event.code == attackKey.value)
        if (!isAttackKey) return

        performLunge()
    }

    @EventHandler
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (ClientAccess.collectiveMinecraft.screen != null) return

        if (autoLungeSetting.value && continuousSetting.value) {
            val keyAttack = ClientAccess.collectiveMinecraft.options.keyAttack
            if (keyAttack.isDown) {
                performLunge()
                return
            }
        }

        if (dDelay > 0) {
            dDelay--
            if (dDelay == 0 && prevSlot != -1) {
                InventoryUtils.selectHotbarSlot(prevSlot)
                prevSlot = -1
            }
        }
    }

    private fun performLunge() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        val originalSlot = player.inventory.selectedSlot
        val registryLookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        val lungeEnchant = registryLookup.getOrThrow(Enchantments.LUNGE)

        var bestSlot = -1
        var bestLevel = 0

        for (i in 0 until 9) {
            val stack: ItemStack = player.inventory.getItem(i)
            val lungeLevel = EnchantmentHelper.getItemEnchantmentLevel(lungeEnchant, stack)
            if (lungeLevel > 0 && lungeLevel >= bestLevel) {
                bestSlot = i
                bestLevel = lungeLevel
            }
        }

        if (bestSlot == -1) {
            for (i in 0 until 9) {
                val stack = player.inventory.getItem(i)
                if (stack.item.toString().contains("spear", ignoreCase = true)) {
                    bestSlot = i
                    break
                }
            }
        }

        if (bestSlot == -1) return

        InventoryUtils.selectHotbarSlot(bestSlot)

        PacketSender.send(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STAB,
                BlockPos.ZERO,
                Direction.DOWN
            )
        )
        player.lungeForwardMaybe()

        if (threeDFlightSetting.value) {
            val look = player.lookAngle
            val factor = 0.5 * (if (bestLevel > 0) bestLevel else 2)
            player.deltaMovement = Vec3(
                player.deltaMovement.x + look.x * factor * 0.4,
                look.y * factor,
                player.deltaMovement.z + look.z * factor * 0.4
            )
        }

        if (swapBackSetting.value) {
            // prevSlot хранит исходный слот ПЕРЕД свапом атрибута: не перетираем его
            // текущим (целевым) слотом, иначе откат возвращает не тот слот.
            val restoreSlot = if (prevSlot != -1) prevSlot else originalSlot
            if (delaySetting.value == 0) {
                InventoryUtils.selectHotbarSlot(restoreSlot)
                prevSlot = -1
                dDelay = 0
            } else {
                prevSlot = restoreSlot
                dDelay = delaySetting.value
            }
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return
        if (isSwapping || autoLungeSetting.value) return

        val packet = event.packet
        if (packet is ServerboundInteractPacket) {
            if (InteractPacketUtils.getAction(packet) == InteractPacketUtils.Action.ATTACK) {
                val entityId = (packet as ServerboundInteractPacketAccessor).entityId
                val target = level.getEntity(entityId)
                if (target != null) {
                    performAttributeSwap(target, false)
                }
            }
        } else if (packet is ServerboundPlayerActionPacket) {
            if (packet.action == ServerboundPlayerActionPacket.Action.STAB) {
                val hit = ClientAccess.collectiveMinecraft.hitResult
                val targetEntity = (hit as? EntityHitResult)?.entity
                if (targetEntity != null) {
                    performAttributeSwap(targetEntity, true)
                }
            }
        }
    }

    private fun performAttributeSwap(targetEntity: Entity, stabbed: Boolean) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (ClientAccess.collectiveMinecraft.level == null) return

        isSwapping = true
        try {
            val originalSlot = player.inventory.selectedSlot
            if (swapBackSetting.value) {
                prevSlot = originalSlot
            }
            didSwap = false

            if (shieldBreakerSetting.value) {
                if (targetEntity is Player) {
                    if (targetEntity.isBlocking) {
                        for (i in 0 until 9) {
                            val stack = player.inventory.getItem(i)
                            if (stack.`is`(ItemTags.AXES)) {
                                InventoryUtils.selectHotbarSlot(i)
                                if (stabbed) {
                                    PacketSender.send(
                                        ServerboundInteractPacket.createAttackPacket(
                                            targetEntity,
                                            player.isShiftKeyDown
                                        )
                                    )
                                }
                                didSwap = true
                                break
                            }
                        }
                    } else if (!noSwapSetting.value) {
                        val target = targetSlotSetting.value - 1
                        InventoryUtils.selectHotbarSlot(target)
                        if (stabbed) {
                            PacketSender.send(
                                ServerboundInteractPacket.createAttackPacket(
                                    targetEntity,
                                    player.isShiftKeyDown
                                )
                            )
                        }
                        didSwap = true
                    }
                } else {
                    val target = targetSlotSetting.value - 1
                    InventoryUtils.selectHotbarSlot(target)
                    if (stabbed) {
                        PacketSender.send(
                            ServerboundInteractPacket.createAttackPacket(
                                targetEntity,
                                player.isShiftKeyDown
                            )
                        )
                    }
                    didSwap = true
                }
            } else {
                val target = targetSlotSetting.value - 1
                InventoryUtils.selectHotbarSlot(target)
                if (stabbed) {
                    PacketSender.send(
                        ServerboundInteractPacket.createAttackPacket(
                            targetEntity,
                            player.isShiftKeyDown
                        )
                    )
                }
                didSwap = true
            }

            if (swapBackSetting.value && didSwap) {
                if (delaySetting.value == 0) {
                    InventoryUtils.selectHotbarSlot(originalSlot)
                    prevSlot = -1
                    dDelay = 0
                } else {
                    dDelay = delaySetting.value
                }
            }
        } finally {
            isSwapping = false
        }
    }
}
