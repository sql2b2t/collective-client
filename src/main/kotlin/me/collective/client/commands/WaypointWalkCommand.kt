package me.collective.client.commands

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalXZ
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class WaypointWalkCommand : Command("walk") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val nameArgument = (argument("name", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>)
            .suggests { context: CommandContext<Any?>, suggestionsBuilder: SuggestionsBuilder ->
                val serverKey = WaypointsCommand.getCurrentServerKey()
                WaypointsCommand.suggestNamesForServer(context, suggestionsBuilder, serverKey)
            }
            .executes { context ->
                if (!ClientContext.baritoneBridge.isAvailable()) {
                    ChatUtils.printChat(Component.literal("Baritone is not installed."), ChatUtils.DEFAULT_SIGNATURE)
                    return@executes 0
                }

                val name = context.getArgument("name", String::class.java)
                val waypoint = WaypointsCommand.resolveWaypointPosition(context)
                if (waypoint == null) {
                    ChatUtils.printChat(
                        Component.literal("Waypoint ").append(formatText(name)).append(" not found"),
                        ChatUtils.DEFAULT_SIGNATURE,
                        ChatUtils.MessagePriority.LOW
                    )
                    return@executes 0
                }

                val provider = BaritoneAPI.getProvider()
                val baritone = provider.primaryBaritone
                val goal = GoalXZ(waypoint.x.toInt(), waypoint.z.toInt())
                val process = baritone.customGoalProcess
                process.setGoalAndPath(goal)
                ChatUtils.printChat(
                    Component.literal("Walking to waypoint ").append(formatText(name)),
                    ChatUtils.DEFAULT_SIGNATURE
                )
                1
            }

        root.then(nameArgument)
    }
}
