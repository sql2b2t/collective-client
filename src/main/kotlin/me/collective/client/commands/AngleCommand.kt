package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.managers.RotationManager
import me.collective.client.modules.player.RotationLockModule
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

class AngleCommand : Command("angle") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        val positionBuilder = argument("pos", PositionArgument()) as RequiredArgumentBuilder<Any?, *>
        val yawLockBuilder = literal<Any?>("yawlock")

        val yawLockAction = { commandContext: com.mojang.brigadier.context.CommandContext<Any?> ->
            val position = commandContext.getArgument("pos", Vec3::class.java)
            val rotations = RotationManager.getRotationsToPosition(position)
            rotationLock?.yawValueSetting?.value = rotations[0]
            1
        }
        val yawLock = yawLockBuilder.executes(yawLockAction)
        val positionWithYawLock = positionBuilder.then(yawLock) as RequiredArgumentBuilder<Any?, *>

        val angleAction = { commandContext: com.mojang.brigadier.context.CommandContext<Any?> ->
            val position = commandContext.getArgument("pos", Vec3::class.java)
            val rotations = RotationManager.getRotationsToPosition(position)
            val messageText = "Calculated angle. Yaw: %.1f, Pitch: %.1f.".format(rotations[0], rotations[1])
            val message = Component.literal(messageText)
            ChatUtils.printChat(message, -1)
            1
        }
        val angle = positionWithYawLock.executes(angleAction)
        root.then(angle)
    }

    companion object {
        private val rotationLock: RotationLockModule? by lazy {
            ClientContext.getModule(RotationLockModule::class.java)
        }
    }
}
