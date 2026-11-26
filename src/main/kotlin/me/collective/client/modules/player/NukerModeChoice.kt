package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.SocialRegistry
import me.collective.client.settings.NamedEntry

import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.Vec3

enum class NukerModeChoice(private val displayName: String) : NamedEntry {
    WHITELIST("WhiteList") {
        override fun matchesBlock(
            blockPos: BlockPos, block: Block, nukerModule: NukerModule
        ): Boolean {
            return nukerModule.blocksSetting.value.contains(block)
        }
    },
    BLACKLIST("BlackList") {
        override fun matchesBlock(
            blockPos: BlockPos, block: Block, nukerModule: NukerModule
        ): Boolean {
            val whitelistMode = WHITELIST
            return !whitelistMode.matchesBlock(blockPos, block, nukerModule)
        }
    },
    SHULKERS("Shulkers") {
        override fun matchesBlock(
            blockPos: BlockPos, block: Block, nukerModule: NukerModule
        ): Boolean {
            if (block !is ShulkerBoxBlock) {
                return false
            }

            if (nukerModule.playerProximitySetting.value) {
                val players = ClientAccess.collectiveMinecraft.level!!.players().iterator()

                while (players.hasNext()) {
                    val player = players.next() as AbstractClientPlayer
                    // ClientAccess.isLocalPlayer is package-private in Java; comparison inlined 1:1.
                    if (ClientAccess.collectiveMinecraft.player!! !== player) {
                        val friendManager: SocialRegistry = ClientContext.socialRegistry
                        if (!friendManager.isFriendPlayer(player)) {
                            val eyePositionSnapshot: Vec3 = player.eyePosition
                            val blockCenter: Vec3 = Vec3.atCenterOf(blockPos)
                            if (!(eyePositionSnapshot.distanceTo(blockCenter) > 6.5)) {
                                return true
                            }
                        }
                    }
                }

                return false
            } else {
                return true
            }
        }
    },
    BEDS("Bed") {
        override fun matchesBlock(
            blockPos: BlockPos, block: Block, nukerModule: NukerModule
        ): Boolean {
            if (block is BedBlock) {
                val stateSnapshot: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
                val partProperty: EnumProperty<BedPart> = BedBlock.PART
                if (stateSnapshot.getValue(partProperty) == BedPart.HEAD) {
                    return true
                }
            }

            return false
        }
    },
    CLICK("Click") {
        override fun matchesBlock(
            blockPos: BlockPos, block: Block, nukerModule: NukerModule
        ): Boolean {
            return nukerModule.getClickedBlock() === block
        }
    };

    override fun getName(): String {
        return this.displayName
    }

    abstract fun matchesBlock(
        blockPos: BlockPos, block: Block, nukerModule: NukerModule
    ): Boolean
}
