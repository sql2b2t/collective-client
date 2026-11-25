package me.collective.client.commands

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.IBaritoneProvider
import baritone.api.command.IBaritoneChatControl
import baritone.api.event.events.ChatEvent
import baritone.api.event.events.TabCompleteEvent
import baritone.api.event.listener.IEventBus
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.concurrent.CompletableFuture

class BaritoneCommand : Command("baritone") {

    init {
        setAliases("b")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        var commandArgument = argument("command", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>

        commandArgument = commandArgument.suggests { context, suggestionsBuilder ->
            if (!ClientContext.baritoneBridge.isAvailable()) {
                return@suggests suggestionsBuilder.buildFuture()
            }
            val provider: IBaritoneProvider = BaritoneAPI.getProvider()
            val baritone: IBaritone = provider.primaryBaritone
            var completionInput = COMPLETION_PREFIX

            try {
                val remainingForCompletion = suggestionsBuilder.remaining
                completionInput = COMPLETION_PREFIX + remainingForCompletion
            } catch (_: Exception) {
            }

            val completionEvent = TabCompleteEvent(completionInput)
            val completeAction = Runnable {
                baritone.gameEventHandler.onPreTabComplete(completionEvent)
            }

            runWithCompletionPrefix(completeAction)
            if (completionEvent.completions != null && completionEvent.completions.isNotEmpty()) {
                val input = context.input
                val measuredInputLength = input.length
                val builderStart = suggestionsBuilder.start
                val remaining = suggestionsBuilder.remaining
                val remainingEnd = builderStart + remaining.length
                val completionEnd = minOf(measuredInputLength, remainingEnd)
                val inputThroughCompletion = input.substring(0, completionEnd)
                val wordStart = inputThroughCompletion.lastIndexOf(" ") + 1
                val wordEnd = inputThroughCompletion.length
                val range = StringRange.between(wordStart, wordEnd)
                val completions = completionEvent.completions
                val suggestionList = completions.map { completion ->
                    Suggestion(range, stripCompletionPrefix(completion))
                }
                val suggestions = Suggestions(range, suggestionList)
                CompletableFuture.completedFuture(suggestions)
            } else {
                suggestionsBuilder.buildFuture()
            }
        }

        val commandWithAction = commandArgument.executes { context ->
            if (!ClientContext.baritoneBridge.isAvailable()) {
                ChatUtils.printChat(Component.literal("Baritone is not installed."), ChatUtils.DEFAULT_SIGNATURE)
                return@executes 0
            }
            val provider: IBaritoneProvider = BaritoneAPI.getProvider()
            val baritone: IBaritone = provider.primaryBaritone

            val command = context.getArgument("command", String::class.java)

            if (!"goto".equals(command, ignoreCase = true)) {
                val chatEventBus: IEventBus = baritone.gameEventHandler
                val chatEvent = ChatEvent(formatForwardedCommand(IBaritoneChatControl.FORCE_COMMAND_PREFIX, command))
                chatEventBus.onSendChatMessage(chatEvent)
                1
            } else {
                val hitResult = ClientAccess.collectiveMinecraft.hitResult
                if (hitResult is BlockHitResult && hitResult.type != HitResult.Type.MISS) {
                    var target: BlockPos = hitResult.blockPos

                    if (!WorldUtils.isReplaceable(target)) {
                        val aboveTarget = target.above()
                        if (WorldUtils.isReplaceable(aboveTarget)) {
                            target = aboveTarget
                        }
                    }

                    ClientContext.baritoneBridge.pathTo(target)
                    1
                } else {
                    val renderer = ClientAccess.collectiveMinecraft.gameRenderer
                    val camera = renderer.mainCamera
                    val cameraPosition = net.minecraft.core.BlockPos.containing(camera.position)
                    ClientContext.baritoneBridge.pathTo(cameraPosition)
                    1
                }
            }
        }
        root.then(commandWithAction)
    }

    private fun runWithCompletionPrefix(action: Runnable) {
        val previousPrefix = BaritoneAPI.getSettings().prefix.value
        BaritoneAPI.getSettings().prefix.value = COMPLETION_PREFIX
        try {
            action.run()
        } finally {
            BaritoneAPI.getSettings().prefix.value = previousPrefix
        }
    }

    companion object {
        private const val COMPLETION_PREFIX = "#"

        private fun formatForwardedCommand(forceCommandPrefix: String, command: String): String {
            return forceCommandPrefix + command
        }

        private fun stripCompletionPrefix(text: String): String {
            return if (text.startsWith(COMPLETION_PREFIX)) {
                text.substring(1)
            } else {
                text
            }
        }
    }
}
