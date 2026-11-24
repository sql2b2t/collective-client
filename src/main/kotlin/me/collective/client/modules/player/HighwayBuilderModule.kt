package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils

import java.awt.Color
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Автоматическое рытье тоннелей и укладка покрытия хайвеев.
 */
class HighwayBuilderModule : Module("HighwayBuilder", "Автоматически копает тоннель и укладывает покрытие хайвея", ModuleCategory.PLAYER) {
    enum class DirectionMode(private val displayName: String) : NamedEntry {
        AUTO("Auto"),
        NORTH("North"),
        SOUTH("South"),
        EAST("East"),
        WEST("West"),
        NORTH_EAST("North-East"),
        NORTH_WEST("North-West"),
        SOUTH_EAST("South-East"),
        SOUTH_WEST("South-West");

        override fun getName(): String {
            return this.displayName
        }
    }

    enum class PaveBlockChoice(private val displayName: String) : NamedEntry {
        NETHERRACK("Netherrack"),
        OBSIDIAN("Obsidian"),
        COBBLESTONE("Cobblestone"),
        ANY_SOLID("Any Solid");

        override fun getName(): String {
            return this.displayName
        }
    }

    val modeSetting: Setting<DirectionMode>
    val lockDirectionSetting: Setting<Boolean>
    val widthSetting: Setting<Int>
    val heightSetting: Setting<Int>
    val reachSetting: Setting<Int>

    val paveFloorSetting: Setting<Boolean>
    val fillGapsSetting: Setting<Boolean>
    val fillLiquidsSetting: Setting<Boolean>
    val railingsSetting: Setting<Boolean>
    val blockChoiceSetting: Setting<PaveBlockChoice>

    val clearTunnelSetting: Setting<Boolean>
    val autoToolSetting: Setting<Boolean>
    val autoWalkSetting: Setting<Boolean>
    val bptSetting: Setting<Int>
    val delaySetting: Setting<Int>
    val durabilityThreshold: Setting<Int>

    val renderSetting: Setting<Boolean>
    val colorBreakSetting: Setting<Color>
    val colorPlaceSetting: Setting<Color>

    private val toBreak: MutableSet<BlockPos> = LinkedHashSet()
    private val toPlace: MutableSet<BlockPos> = LinkedHashSet()
    private val delayTimer: ElapsedTimer = ElapsedTimer()
    private val warnTimer: ElapsedTimer = ElapsedTimer()

    private var activeDirection: DirectionMode = DirectionMode.SOUTH
    private var targetYaw: Float = 0.0F

    init {
        this.modeSetting = this.add(EnumSetting<DirectionMode>("Direction", DirectionMode.AUTO))
        this.lockDirectionSetting = this.add(BooleanSetting("LockDirection", true))
        this.widthSetting = this.add(NumberSetting<Int>("Width", 3, 1, 7))
        this.heightSetting = this.add(NumberSetting<Int>("Height", 3, 2, 5))
        this.reachSetting = this.add(NumberSetting<Int>("Reach", 2, 1, 4))

        this.paveFloorSetting = this.add(BooleanSetting("PaveFloor", true))
        this.fillGapsSetting = this.add(BooleanSetting("FillGaps", true))
        this.fillLiquidsSetting = this.add(BooleanSetting("FillLiquids", true))
        this.railingsSetting = this.add(BooleanSetting("Railings", false))
        this.blockChoiceSetting = this.add(EnumSetting<PaveBlockChoice>("Block", PaveBlockChoice.ANY_SOLID))

        this.clearTunnelSetting = this.add(BooleanSetting("ClearTunnel", true))
        this.autoToolSetting = this.add(BooleanSetting("AutoTool", true))
        this.autoWalkSetting = this.add(BooleanSetting("AutoWalk", true))
        this.bptSetting = this.add(NumberSetting<Int>("BlocksPerTick", 2, 1, 6))
        this.delaySetting = this.add(NumberSetting<Int>("Delay", 1, 0, 5))
        this.durabilityThreshold = this.add(NumberSetting<Int>("DurabilityThreshold", 20, 5, 50))

        this.renderSetting = this.add(BooleanSetting("Render", true))
        this.colorBreakSetting = this.add(ColorSetting("BreakColor", Color(255, 60, 60, 90)))
        this.colorPlaceSetting = this.add(ColorSetting("PlaceColor", Color(50, 220, 140, 90)))
    }

    override fun onEnable() {
        val player = ClientAccess.collectiveMinecraft.player
        if (player != null) {
            if (this.modeSetting.value == DirectionMode.AUTO) {
                this.activeDirection = resolveDirection(player.yRot)
            } else {
                this.activeDirection = this.modeSetting.value
            }
            this.targetYaw = getDirectionYaw(this.activeDirection)
        }
        this.toBreak.clear()
        this.toPlace.clear()
        this.delayTimer.reset()
    }

    override fun onDisable() {
        this.toBreak.clear()
        this.toPlace.clear()
    }

    @EventHandler
    private fun onPlayerTick(event: LocalPlayerTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player
        val level = ClientAccess.collectiveMinecraft.level
        if (player == null || level == null) {
            return
        }

        if (this.isToolLowDurability(player)) {
            if (this.warnTimer.hasElapsed(4000L)) {
                ChatUtils.printChat(
                    Component.literal("§c[HighwayBuilder] Инструмент повреждён! Приостановка работы."),
                    ChatUtils.createSignature(-3),
                    ChatUtils.MessagePriority.HIGH
                )
                this.warnTimer.reset()
            }
            return
        }

        if (this.modeSetting.value != DirectionMode.AUTO) {
            this.activeDirection = this.modeSetting.value
            this.targetYaw = getDirectionYaw(this.activeDirection)
        }

        if (this.lockDirectionSetting.value) {
            ClientContext.rotationManager.requestRotation(floatArrayOf(this.targetYaw, 10.0F), 45, false)
        }

        this.scanHighwayGeometry(player, level)

        // Ходьба запрашивается КАЖДЫЙ тик: это per-tick override ввода,
        // пропуск тиков из-за таймера задержки даёт «дёрганое» движение.
        if (this.autoWalkSetting.value) {
            this.handleAutoMovement(player)
        }

        if (!this.delayTimer.hasElapsed(this.delaySetting.value * 50L)) {
            return
        }

        val bpt = this.bptSetting.value
        var actionsDone = 0

        if (this.toPlace.isNotEmpty()) {
            val placeBatch: List<BlockPos> = ArrayList(this.toPlace)
            for (pos in placeBatch) {
                if (actionsDone >= bpt) break
                if (this.executePlacement(player, level, pos)) {
                    this.toPlace.remove(pos)
                    actionsDone++
                }
            }
        }

        if (actionsDone < bpt && this.toBreak.isNotEmpty()) {
            val breakBatch: List<BlockPos> = ArrayList(this.toBreak)
            for (pos in breakBatch) {
                if (actionsDone >= bpt) break
                if (this.executeMining(player, level, pos)) {
                    this.toBreak.remove(pos)
                    actionsDone++
                }
            }
        }

        if (actionsDone > 0) {
            this.delayTimer.reset()
        }
    }

    @EventHandler
    private fun onRender(event: WorldRenderEvent.Main) {
        if (!this.renderSetting.value) {
            return
        }

        val poses = event.poseStack!!
        val breakFill = this.colorBreakSetting.value
        val placeFill = this.colorPlaceSetting.value
        val breakOutline = Color(breakFill.red, breakFill.green, breakFill.blue, 220)
        val placeOutline = Color(placeFill.red, placeFill.green, placeFill.blue, 220)

        for (pos in this.toBreak) {
            WorldBoxRenderer.fillBox(poses, pos, breakFill)
            WorldBoxRenderer.outlineBox(poses, pos, breakOutline, 1.5F)
        }

        for (pos in this.toPlace) {
            WorldBoxRenderer.fillBox(poses, pos, placeFill)
            WorldBoxRenderer.outlineBox(poses, pos, placeOutline, 1.5F)
        }
    }

    private fun scanHighwayGeometry(player: LocalPlayer, level: ClientLevel) {
        this.toBreak.clear()
        this.toPlace.clear()

        val forward = getForwardStep(this.activeDirection)
        val right = getRightStep(this.activeDirection)

        val originX = player.blockX
        val originY = player.blockY
        val originZ = player.blockZ
        val floorY = originY - 1

        val width = this.widthSetting.value
        val height = this.heightSetting.value
        val reach = this.reachSetting.value

        val sideStart = -(width / 2)
        val sideEnd = sideStart + width - 1

        for (d in 0..reach) {
            for (w in sideStart..sideEnd) {
                val blockX = originX + d * forward[0] + w * right[0]
                val blockZ = originZ + d * forward[1] + w * right[1]

                if (this.paveFloorSetting.value) {
                    val floorPos = BlockPos(blockX, floorY, blockZ)
                    if (this.needsPaving(level, floorPos)) {
                        this.toPlace.add(floorPos)
                    }

                    if (this.fillGapsSetting.value) {
                        val gapPos1 = floorPos.below()
                        if (this.needsPaving(level, gapPos1)) {
                            this.toPlace.add(gapPos1)
                        }
                        val gapPos2 = gapPos1.below()
                        if (this.needsPaving(level, gapPos2)) {
                            this.toPlace.add(gapPos2)
                        }
                    }
                }

                if (this.railingsSetting.value && (w == sideStart || w == sideEnd)) {
                    val railingPos = BlockPos(blockX, floorY + 1, blockZ)
                    if (level.getBlockState(railingPos).isAir) {
                        this.toPlace.add(railingPos)
                    }
                }

                if (this.clearTunnelSetting.value) {
                    for (h in 1..height) {
                        if (this.railingsSetting.value && h == 1 && (w == sideStart || w == sideEnd)) {
                            continue
                        }
                        val clearPos = BlockPos(blockX, floorY + h, blockZ)
                        val state: BlockState = level.getBlockState(clearPos)
                        if (!state.isAir) {
                            if (!level.getFluidState(clearPos).isEmpty) {
                                if (this.fillLiquidsSetting.value) {
                                    this.toPlace.add(clearPos)
                                }
                            } else if (state.getDestroySpeed(level, clearPos) >= 0.0F) {
                                this.toBreak.add(clearPos)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun needsPaving(level: ClientLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        if (state.isAir || !level.getFluidState(pos).isEmpty) {
            return true
        }
        return !state.isSolid
    }

    private fun executePlacement(player: LocalPlayer, level: ClientLevel, targetPos: BlockPos): Boolean {
        val slot = this.findPavingSlot(player)
        if (slot == -1) {
            if (this.warnTimer.hasElapsed(4000L)) {
                ChatUtils.printChat(
                    Component.literal("§c[HighwayBuilder] Нет подходящих блоков для укладки!"),
                    ChatUtils.createSignature(-3),
                    ChatUtils.MessagePriority.HIGH
                )
                this.warnTimer.reset()
            }
            return false
        }

        val previousSlot = InventoryUtils.getSelectedSlot()
        if (slot != previousSlot) {
            InventoryUtils.selectHotbarSlot(slot)
        }

        val placed = BlockInteractionUtils.placeBlock(targetPos, true, InteractionHand.MAIN_HAND)

        if (slot != previousSlot) {
            InventoryUtils.selectHotbarSlot(previousSlot)
        }

        return placed
    }

    private fun executeMining(player: LocalPlayer, level: ClientLevel, targetPos: BlockPos): Boolean {
        if (this.autoToolSetting.value) {
            val bestToolSlot = InventoryUtils.findBestToolSlot(targetPos, false)
            if (bestToolSlot != -1 && bestToolSlot != InventoryUtils.getSelectedSlot()) {
                InventoryUtils.selectHotbarSlot(bestToolSlot)
            }
        }

        val speedMine = speedMineModule
        if (speedMine != null && speedMine.isToggled()) {
            SpeedMineModule.automatedMiningStart = true
            ClientAccess.collectiveMinecraft.gameMode!!.startDestroyBlock(targetPos, Direction.UP)
            SpeedMineModule.automatedMiningStart = false
        } else {
            PacketSender.sendPlayerAction(
                Action.START_DESTROY_BLOCK,
                targetPos,
                Direction.UP
            )
            ClientAccess.collectiveMinecraft.gameMode!!.continueDestroyBlock(targetPos, Direction.UP)
            PacketSender.sendSwing(InteractionHand.MAIN_HAND)
        }

        return true
    }

    private fun handleAutoMovement(player: LocalPlayer) {
        val forward = getForwardStep(this.activeDirection)
        val floorY = player.blockY - 1
        val originX = player.blockX
        val originZ = player.blockZ

        val width = this.widthSetting.value
        val sideStart = -(width / 2)
        val sideEnd = sideStart + width - 1

        var sliceBlocked = false
        for (w in sideStart..sideEnd) {
            val checkX = originX + forward[0] + w * getRightStep(this.activeDirection)[0]
            val checkZ = originZ + forward[1] + w * getRightStep(this.activeDirection)[1]

            val floorPos = BlockPos(checkX, floorY, checkZ)
            if (this.toPlace.contains(floorPos)) {
                sliceBlocked = true
                break
            }

            for (h in 1..Math.min(2, this.heightSetting.value)) {
                val headPos = BlockPos(checkX, floorY + h, checkZ)
                if (this.toBreak.contains(headPos)) {
                    sliceBlocked = true
                    break
                }
            }
            if (sliceBlocked) break
        }

        if (!sliceBlocked) {
            ClientContext.inputOverrides.requestForwardMovement(1.0F)
            ClientContext.inputOverrides.requestForward(true)

            val stepPos = BlockPos(originX + forward[0], floorY + 1, originZ + forward[1])
            if (!ClientAccess.collectiveMinecraft.level!!.getBlockState(stepPos).isAir) {
                ClientContext.inputOverrides.requestJump(true)
            }
        }
    }

    private fun findPavingSlot(player: LocalPlayer): Int {
        val choice = this.blockChoiceSetting.value
        val predicate = Predicate<ItemStack> { stack ->
            if (stack == null || stack.isEmpty || stack.item !is BlockItem) {
                false
            } else {
                val blockItem = stack.item as BlockItem
                val block = blockItem.block
                if (block === Blocks.AIR || block === Blocks.CAVE_AIR || block === Blocks.VOID_AIR) {
                    false
                } else if (!block.defaultBlockState().isSolid) {
                    false
                } else if (block === Blocks.SAND || block === Blocks.RED_SAND || block === Blocks.GRAVEL) {
                    false
                } else if (block === Blocks.CHEST || block === Blocks.ENDER_CHEST || block === Blocks.SHULKER_BOX) {
                    false
                } else {
                    when (choice) {
                        PaveBlockChoice.NETHERRACK -> block === Blocks.NETHERRACK
                        PaveBlockChoice.OBSIDIAN -> block === Blocks.OBSIDIAN || block === Blocks.CRYING_OBSIDIAN
                        PaveBlockChoice.COBBLESTONE -> block === Blocks.COBBLESTONE || block === Blocks.COBBLED_DEEPSLATE
                        PaveBlockChoice.ANY_SOLID -> true
                    }
                }
            }
        }

        if (predicate.test(player.mainHandItem)) {
            return InventoryUtils.getSelectedSlot()
        }

        val hotbarSlot = InventoryUtils.findHotbarSlot(predicate)
        if (hotbarSlot != -1) {
            return hotbarSlot
        }

        val invSlot = InventoryUtils.findInventorySlot(predicate, true)
        if (invSlot != -1) {
            val targetSlot = InventoryUtils.getSelectedSlot()
            InventoryUtils.swapWithHotbarSlot(invSlot, targetSlot)
            return targetSlot
        }

        return -1
    }

    private fun isToolLowDurability(player: LocalPlayer): Boolean {
        val main = player.mainHandItem
        if (main.isDamageableItem) {
            val remaining = main.maxDamage - main.damageValue
            return remaining <= this.durabilityThreshold.value
        }
        return false
    }

    companion object {
        private var speedMineModule: SpeedMineModule? = null

        @JvmStatic
        fun bindReferencedModules(registry: ModuleRegistry) {
            speedMineModule = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule?
        }

        @JvmStatic
        fun resolveDirection(yaw: Float): DirectionMode {
            val wrapped = Mth.wrapDegrees(yaw)
            if (wrapped >= -22.5F && wrapped < 22.5F) return DirectionMode.SOUTH
            if (wrapped >= 22.5F && wrapped < 67.5F) return DirectionMode.SOUTH_WEST
            if (wrapped >= 67.5F && wrapped < 112.5F) return DirectionMode.WEST
            if (wrapped >= 112.5F && wrapped < 157.5F) return DirectionMode.NORTH_WEST
            if (wrapped >= 157.5F || wrapped < -157.5F) return DirectionMode.NORTH
            if (wrapped >= -157.5F && wrapped < -112.5F) return DirectionMode.NORTH_EAST
            if (wrapped >= -112.5F && wrapped < -67.5F) return DirectionMode.EAST
            return DirectionMode.SOUTH_EAST
        }

        @JvmStatic
        fun getDirectionYaw(mode: DirectionMode): Float {
            return when (mode) {
                DirectionMode.SOUTH -> 0.0F
                DirectionMode.SOUTH_WEST -> 45.0F
                DirectionMode.WEST -> 90.0F
                DirectionMode.NORTH_WEST -> 135.0F
                DirectionMode.NORTH -> 180.0F
                DirectionMode.NORTH_EAST -> -135.0F
                DirectionMode.EAST -> -90.0F
                DirectionMode.SOUTH_EAST -> -45.0F
                else -> 0.0F
            }
        }

        @JvmStatic
        fun getForwardStep(mode: DirectionMode): IntArray {
            return when (mode) {
                DirectionMode.NORTH -> intArrayOf(0, -1)
                DirectionMode.SOUTH -> intArrayOf(0, 1)
                DirectionMode.EAST -> intArrayOf(1, 0)
                DirectionMode.WEST -> intArrayOf(-1, 0)
                DirectionMode.NORTH_EAST -> intArrayOf(1, -1)
                DirectionMode.NORTH_WEST -> intArrayOf(-1, -1)
                DirectionMode.SOUTH_EAST -> intArrayOf(1, 1)
                DirectionMode.SOUTH_WEST -> intArrayOf(-1, 1)
                else -> intArrayOf(0, 1)
            }
        }

        @JvmStatic
        fun getRightStep(mode: DirectionMode): IntArray {
            return when (mode) {
                DirectionMode.NORTH -> intArrayOf(1, 0)
                DirectionMode.SOUTH -> intArrayOf(-1, 0)
                DirectionMode.EAST -> intArrayOf(0, 1)
                DirectionMode.WEST -> intArrayOf(0, -1)
                DirectionMode.NORTH_EAST -> intArrayOf(1, 1)
                DirectionMode.NORTH_WEST -> intArrayOf(1, -1)
                DirectionMode.SOUTH_EAST -> intArrayOf(-1, 1)
                DirectionMode.SOUTH_WEST -> intArrayOf(-1, -1)
                else -> intArrayOf(-1, 0)
            }
        }
    }
}
