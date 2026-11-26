package me.collective.client.commands

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class SeenCommand : TwoB2tApiCommand("seen") {
    private val lastSeenFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH)

    init {
        this.setAliases("lastseen")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val nameArgument = (argument("name", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { commandContext ->
            val playerName = commandContext.getArgument("name", String::class.java)
            val fetchingMessage = Component.nullToEmpty("Fetching data...")
            val fetchingSignature = ChatUtils.objectSignature(this)
            ChatUtils.printChat(fetchingMessage, fetchingSignature)

            val request = this.fetchData("seen", playerName)
            request.whenComplete { seenData: JsonObject?, requestFailure: Throwable? ->
                var errorDetail: String? = null
                if (requestFailure != null) {
                    errorDetail = requestFailure.message
                } else if (seenData != null) {
                    try {
                        if (seenData.has("lastSeen")) {
                            val lastSeenNullableValue = seenData.get("lastSeen")
                            if (lastSeenNullableValue.isJsonNull) {
                                throw RuntimeException("No data for player.")
                            }

                            val lastSeenText = lastSeenNullableValue.asString
                            val lastSeenTime = OffsetDateTime.parse(lastSeenText)
                            val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
                            val timeSinceLastSeen = Duration.between(lastSeenTime, currentTime)
                            val seenSummary = StringBuilder(playerName)
                            val elapsedSeconds = timeSinceLastSeen.toSeconds().toInt()
                            if (elapsedSeconds == 0) {
                                seenSummary.append(" is online right now.")
                            } else {
                                seenSummary.append(" was seen ")
                                val durationText = formatDuration(elapsedSeconds)
                                seenSummary.append(durationText)
                                seenSummary.append(" ago (")
                                val formattedLastSeen = this.lastSeenFormatter.format(lastSeenTime)
                                seenSummary.append(formattedLastSeen)
                                seenSummary.append(").")
                            }

                            val summaryMessage = Component.nullToEmpty(seenSummary.toString())
                            val summarySignature = ChatUtils.objectSignature(this)
                            ChatUtils.printChat(summaryMessage, summarySignature)
                            return@whenComplete
                        }

                        errorDetail = "Response data doesn't have a required field: lastSeen."
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
