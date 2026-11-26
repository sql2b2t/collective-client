package me.collective.client.commands

import com.google.gson.JsonObject
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class QueueCommand : TwoB2tApiCommand("queue") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        root.executes {
            val fetchingMessage = Component.nullToEmpty("Fetching data...")
            val fetchingSignature = ChatUtils.objectSignature(this)
            ChatUtils.printChat(fetchingMessage, fetchingSignature)

            val request = this.fetchData("queue", null)
            request.whenComplete { queueData: JsonObject?, requestFailure: Throwable? ->
                var errorDetail: String? = null
                if (requestFailure != null) {
                    errorDetail = requestFailure.message
                } else if (queueData != null) {
                    try {
                        var priorityQueue = -1337
                        var regularQueue = -1337
                        if (queueData.has("prio")) {
                            priorityQueue = queueData.get("prio").asInt
                        }
                        if (queueData.has("regular")) {
                            regularQueue = queueData.get("regular").asInt
                        }

                        val queueSummary = StringBuilder()
                        if (regularQueue != -1337) {
                            queueSummary.append("%d in normal queue".format(regularQueue))
                        }
                        if (regularQueue != -1337 && priorityQueue != -1337) {
                            queueSummary.append(", ")
                        }
                        if (priorityQueue != -1337) {
                            queueSummary.append("%d in priority queue".format(priorityQueue))
                        }

                        val summaryText = queueSummary.toString()
                        val summaryMessage = Component.nullToEmpty(summaryText)
                        val summarySignature = ChatUtils.objectSignature(this)
                        ChatUtils.printChat(summaryMessage, summarySignature)
                        return@whenComplete
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
    }
}
