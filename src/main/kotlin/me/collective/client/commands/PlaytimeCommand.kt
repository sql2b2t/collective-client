package me.collective.client.commands

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class PlaytimeCommand : TwoB2tApiCommand("playtime") {

    init {
        this.setAliases("pt")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val nameArgument = (argument("name", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { commandContext ->
            val playerName = commandContext.getArgument("name", String::class.java)
            val fetchingMessage = Component.nullToEmpty("Fetching data...")
            val fetchingSignature = ChatUtils.objectSignature(this)
            ChatUtils.printChat(fetchingMessage, fetchingSignature)

            val request = this.fetchData("playtime", playerName)
            request.whenComplete { playtimeData: JsonObject?, requestFailure: Throwable? ->
                var errorDetail: String? = null
                if (requestFailure != null) {
                    errorDetail = requestFailure.message
                } else if (playtimeData != null) {
                    try {
                        if (playtimeData.has("playtimeSeconds")) {
                            val playtimeNullableValue = playtimeData.get("playtimeSeconds")
                            if (playtimeNullableValue.isJsonNull) {
                                throw RuntimeException("No data for player.")
                            }
                            val totalPlaytimeSeconds = playtimeNullableValue.asInt
                            val durationText = formatDuration(totalPlaytimeSeconds)
                            val summaryText = "$playerName has played for $durationText."
                            val summaryMessage = Component.nullToEmpty(summaryText)
                            val summarySignature = ChatUtils.objectSignature(this)
                            ChatUtils.printChat(summaryMessage, summarySignature)
                            return@whenComplete
                        }
                        errorDetail = "Response data doesn't have a required field: playtimeSeconds."
                    } catch (responseFailure: Exception) {
                        errorDetail = responseFailure.message
                    }
                }

                if (errorDetail != null) {
                    val errorText = "${ChatFormatting.RED}$errorDetail"
                    val errorMessage = Component.nullToEmpty(errorText)
                    val errorSignature = ChatUtils.objectSignature(this)
                    ChatUtils.printChat(errorMessage, errorSignature, ChatUtils.MessagePriority.MID)
                }
            }
            1
        }
        root.then(nameArgument)
    }
}
