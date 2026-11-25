package me.collective.client.commands

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.DesktopUtils
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.network.HttpRequests
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.CompletableFuture

class NamehistoryCommand : Command("namehistory") {
    private var pendingHistory: CompletableFuture<List<NameHistoryEntry>?>? = null

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val historyArgument = (argument("name", PlayerNameArgumentType()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val pending = pendingHistory
            if (pending != null && !pending.isDone && !pending.isCompletedExceptionally) {
                return@executes 1
            }

            val playerName = context.getArgument("name", String::class.java)

            val future = lookupNameHistory(playerName)
            pendingHistory = future

            future.thenAccept { result ->
                ClientContext.taskScheduler.scheduleRotationTask({ showNameHistory(result) }, 0)
            }
            future.exceptionally { lookupFailure ->
                ClientContext.taskScheduler.scheduleRotationTask({ showLookupFailure(lookupFailure) }, 0)
                null
            }

            val loadingMessage = Component.literal("Loading name history for ").append(formatText(playerName))
            ChatUtils.printChat(loadingMessage, ChatUtils.createSignature(-3))
            1
        }
        root.then(historyArgument)

        val openBuilder = literal<Any?>("open")
        val openArgument = (argument("name", PlayerNameArgumentType()) as RequiredArgumentBuilder<Any?, *>).executes { openContext ->
            try {
                val name = openContext.getArgument("name", String::class.java)
                val profileUrl = URL("https://namemc.com/profile/$name")
                val profileUri = profileUrl.toURI()
                DesktopUtils.browse(profileUri)
            } catch (ignored: Throwable) {
            }
            1
        }
        root.then(openBuilder.then(openArgument))
    }

    private fun showLookupFailure(failure: Throwable) {
        val cause = failure.cause ?: failure
        val message = cause.message ?: "Unknown error"
        val errorMessage = Component.literal(message)
        ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-3), ChatUtils.MessagePriority.HIGH)
    }

    private fun parseNameHistory(responseBody: String): List<NameHistoryEntry>? {
        val parsedJson = JsonParser.parseString(responseBody)
        if (!parsedJson.isJsonArray) {
            return null
        }
        val entries = parsedJson.asJsonArray
        return entries.asList().map { entryJson ->
            ClientAccess.gson.fromJson(entryJson, NameHistoryEntry::class.java)
        }
    }

    private fun lookupNameHistory(playerName: String): CompletableFuture<List<NameHistoryEntry>?> {
        return CompletableFuture.supplyAsync(
            {
                val profileUrl = "https://api.minecraftservices.com/minecraft/profile/lookup/name/$playerName"
                val profileRequest = HttpRequests.get(profileUrl).build()
                val profileBody = HttpRequests.send(profileRequest).body() as String
                val parsedProfile = JsonParser.parseString(profileBody)
                if (parsedProfile.isJsonObject) {
                    val profileObject = parsedProfile.asJsonObject
                    if (profileObject.has("id")) {
                        val profileId = profileObject.get("id").asString
                        val historyUrl = "https://laby.net/api/user/$profileId/get-names"
                        val historyRequest = HttpRequests.get(historyUrl).build()
                        val historyBody = HttpRequests.send(historyRequest).body() as String
                        return@supplyAsync parseNameHistory(historyBody)
                    }
                }
                throw RuntimeException("Player not found")
            },
            ClientAccess.executorService
        )
    }

    private fun showNameHistory(history: List<NameHistoryEntry>?) {
        if (history != null && history.isNotEmpty()) {
            try {
                val message = Component.empty()
                val lastEntry = history.last()
                val lastUsername = lastEntry.displayUsername()
                message.append(formatText(lastUsername))
                message.append("'s Name History: \n")

                val lines = StringBuilder()
                for (index in history.indices) {
                    val entry = history[index]
                    val lineNumber = index + 1
                    val line = "%d. %s (%s)".format(lineNumber, entry.displayUsername(), entry.formatChangeDate())
                    lines.append(line)
                    if (index != history.size - 1) {
                        lines.append('\n')
                    }
                }
                message.append(lines.toString())
                ChatUtils.printChat(message, ChatUtils.createSignature(-3))
            } catch (formatFailure: Exception) {
                formatFailure.printStackTrace()
            }
        }
    }

    data class NameHistoryEntry(
        @SerializedName("username") val username: String? = null,
        @SerializedName("changed_at") val changedAt: String? = null,
        @SerializedName("hidden") val hidden: Boolean = false
    ) {
        fun formatChangeDate(): String {
            if (this.changedAt == null) {
                return "first name"
            }
            val localTimestamp = this.changedAt.split("+")[0]
            return try {
                val parsedTime = LocalDateTime.parse(localTimestamp)
                val localZone = ZoneId.systemDefault()
                val instant = parsedTime.atZone(localZone).toInstant()
                val date = Date.from(instant)
                CHANGE_DATE_FORMAT.format(date)
            } catch (parseFailure: Exception) {
                localTimestamp
            }
        }

        fun displayUsername(): String {
            return if (this.hidden) "HIDDEN" else (this.username ?: "")
        }

        companion object {
            private val CHANGE_DATE_FORMAT = SimpleDateFormat("d MMM yyyy HH:mm:ss")
        }
    }
}
