package me.collective.client.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.network.PacketSender
import me.collective.mixin.MinecraftAccessor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.equipment.Equippable
import java.util.function.BiPredicate
import java.util.function.IntFunction
import java.util.function.Predicate

class ActionCommand : Command("action") {
    private val swapHistory = HashMap<SwapMode, MutableList<SwapHistoryEntry>>()
    private var actionBlocked = false

    init {
        ClientAccess.eventBus.subscribe(this)
        for (mode in SwapMode.entries) {
            swapHistory[mode] = ArrayList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        var swapModeArgumentBuilder = argument("swap", EnumArgumentType(SwapMode::class.java)) as RequiredArgumentBuilder<Any?, *>

        val itemRegistry = BuiltInRegistries.ITEM
        val itemFilter = BiPredicate<CommandContext<*>, Item> { context, item -> acceptsSwapItem(context, item) }
        val itemArrayFactory = IntFunction<Array<Item>> { size -> arrayOfNulls<Item>(size) as Array<Item> }

        val itemType: ArgumentType<Array<Item>> = RegistryEntryArgument.aliased(itemRegistry, itemFilter, itemArrayFactory)
        val itemArgument = (argument("item", itemType) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val items = context.getArgument("item", Array<Item>::class.java) as Array<Item>
            val swapMode = context.getArgument("swap", SwapMode::class.java) as SwapMode
            val strategyForLookup = swapMode.strategy
            val slots = strategyForLookup.findSlots(items)

            if (slots.isInvalid()) {
                this.actionBlocked = true
            }

            val entry = SwapHistoryEntry(swapMode.strategy, slots)
            this.swapHistory[swapMode]?.add(entry)

            if (!slots.isInvalid()) {
                swapMode.strategy.apply(slots)
            }

            1
        }
        swapModeArgumentBuilder = swapModeArgumentBuilder.then(itemArgument) as RequiredArgumentBuilder<Any?, *>

        val backArgument = literal<Any?>("back").executes { context ->
            val swapMode = context.getArgument("swap", SwapMode::class.java) as SwapMode
            val history = this.swapHistory[swapMode] ?: return@executes 1
            if (history.isEmpty()) {
                return@executes 1
            }

            val entry = history.removeLast()
            val savedSlots = entry.slots

            if (savedSlots.isInvalid()) {
                return@executes 1
            }

            entry.strategy.undo(entry.slots)
            1
        }
        val swapWithBack = swapModeArgumentBuilder.then(backArgument)
        root.then(swapWithBack)

        val attackArgument = literal<Any?>("attack").executes {
            if (this.actionBlocked) {
                return@executes 1
            }

            val attackAction = Runnable { (ClientAccess.collectiveMinecraft as MinecraftAccessor).attack() }
            if (ClientAccess.collectiveMinecraft.screen is ChatScreen) {
                this.scheduleRotationTask(attackAction)
            } else {
                attackAction.run()
            }

            1
        }
        root.then(attackArgument)

        val interactArgument = literal<Any?>("interact").executes {
            if (this.actionBlocked) {
                return@executes 1
            }

            val interactAction = Runnable { (ClientAccess.collectiveMinecraft as MinecraftAccessor).interact() }
            if (ClientAccess.collectiveMinecraft.screen is ChatScreen) {
                this.scheduleRotationTask(interactAction)
            } else {
                interactAction.run()
            }

            1
        }
        root.then(interactArgument)

        val jumpArgument = literal<Any?>("jump").executes {
            val inputOverrides = ClientContext.inputOverrides
            inputOverrides.requestJump(true)
            1
        }
        root.then(jumpArgument)

        val takeoffArgument = literal<Any?>("takeoff").executes {
            if (!this.actionBlocked) {
                if (PlayerStateUtils.isLocalWearingElytra()) {
                    val playerForGroundCheck = ClientAccess.collectiveMinecraft.player
                    if (playerForGroundCheck != null && !playerForGroundCheck.onGround()) {
                        PacketSender.sendStartFallFlying()
                        val playerForTakeoff = ClientAccess.collectiveMinecraft.player
                        playerForTakeoff?.startFallFlying()
                    }
                }
            }

            1
        }
        root.then(takeoffArgument)

        val inventoryCommand = InventoryCommand()
        this.addSubcommand(inventoryCommand, root)
    }

    private fun acceptsSwapItem(context: CommandContext<*>, item: Item): Boolean {
        val mode = context.getArgument("swap", SwapMode::class.java) as SwapMode
        return mode.acceptsItem(item)
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        this.actionBlocked = false
    }

    companion object {
        @JvmStatic
        fun canEquip(item: Item): Boolean {
            val stackForComponent = item.defaultInstance
            val equippableType = DataComponents.EQUIPPABLE
            val equippable: Equippable? = stackForComponent.get(equippableType)
            if (equippable != null) {
                val playerForType = ClientAccess.collectiveMinecraft.player ?: return false
                val playerType: EntityType<*> = playerForType.type
                if (equippable.canBeEquippedBy(playerType)) {
                    return true
                }
            }
            return false
        }
    }

    private enum class SwapMode(
        private val modeName: String,
        private val itemFilter: Predicate<Item>,
        val strategy: ActionSwapStrategy
    ) : NamedEntry {
        SWAP("swap", Predicate { true }, HotbarSwapStrategy()),
        ALTERNATIVE("alternative", Predicate { true }, AlternativeSwapStrategy()),
        ARMOR("armor", Predicate { canEquip(it) }, ArmorSwapStrategy()),
        OFFHAND("offhand", Predicate { true }, OffhandSwapStrategy());

        override fun getName(): String = this.modeName

        fun acceptsItem(item: Item): Boolean = this.itemFilter.test(item)
    }

    private data class SwapHistoryEntry(
        val strategy: ActionSwapStrategy,
        val slots: SwapSlots
    )
}
