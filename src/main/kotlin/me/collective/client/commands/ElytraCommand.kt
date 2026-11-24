package me.collective.client.commands

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalXZ
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.network.PacketSender
import net.minecraft.network.chat.Component

class ElytraCommand : Command("elytra") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val waypointArgument = argument("name", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>
        val suggestions = { context: com.mojang.brigadier.context.CommandContext<Any?>, suggestionsBuilder: com.mojang.brigadier.suggestion.SuggestionsBuilder ->
            val waypointCategory = WaypointsCommand.getCurrentServerKey()
            WaypointsCommand.suggestNamesForServer(context, suggestionsBuilder, waypointCategory)
        }
        val suggestedWaypointArgument = waypointArgument.suggests(suggestions)

        val execution = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            if (!ClientContext.baritoneBridge.isAvailable()) {
                ChatUtils.printChat(Component.literal("Baritone is not installed."), ChatUtils.DEFAULT_SIGNATURE)
                0
            } else {
                val waypoint = WaypointsCommand.resolveWaypointPosition(context)
                val waypointName = context.getArgument("name", String::class.java)
                if (waypoint == null) {
                    ChatUtils.printChat(
                        Component.literal("Waypoint ")
                            .append(formatText(waypointName))
                            .append(" not found"),
                        ChatUtils.DEFAULT_SIGNATURE,
                        ChatUtils.MessagePriority.LOW
                    )
                    0
                } else {
                    val provider = BaritoneAPI.getProvider()
                    val baritone = provider.primaryBaritone

                    val goal = GoalXZ(waypoint.x.toInt(), waypoint.z.toInt())
                    val customGoalProcess = baritone.customGoalProcess
                    customGoalProcess.setGoal(goal)
                    val elytraProcess = baritone.elytraProcess
                    elytraProcess.pathTo(goal)

                    ChatUtils.printChat(
                        Component.literal("Flying to waypoint ").append(formatText(waypointName)),
                        ChatUtils.DEFAULT_SIGNATURE
                    )

                    val player = ClientAccess.collectiveMinecraft.player
                    if (player != null && !player.isFallFlying) {
                        if (player.onGround()) {
                            player.push(0.0, 0.42, 0.0)
                        }

                        val fallFlyingTask = Runnable {
                            PacketSender.sendStartFallFlying()
                            ClientAccess.collectiveMinecraft.player?.startFallFlying()
                        }
                        scheduleRotationTask(fallFlyingTask)
                    }
                    1
                }
            }
        }
        val executableWaypointArgument = suggestedWaypointArgument.executes(execution)
        root.then(executableWaypointArgument)
    }
}
