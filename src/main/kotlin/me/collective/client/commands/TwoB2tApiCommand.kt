package me.collective.client.commands

import com.google.gson.JsonObject
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.CollectiveClient
import me.collective.client.utils.network.HttpRequests
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.regex.Pattern

open class TwoB2tApiCommand(commandName: String) : Command(commandName) {

    override fun exec(builder: LiteralArgumentBuilder<*>) {
    }

    fun fetchData(endpoint: String, playerName: String?): CompletableFuture<JsonObject> {
        if (!playerName.isNullOrEmpty() && (playerName.length < 3 || !USERNAME_PATTERN.matcher(playerName).matches())) {
            return CompletableFuture.failedFuture(IllegalArgumentException("Invalid username provided."))
        }
        val path = endpoint + (if (playerName.isNullOrEmpty()) "" else "?playerName=$playerName")
        return CompletableFuture.supplyAsync({
            var lastFailure: RuntimeException? = null
            for (attempt in 0 until 2) {
                val response: HttpResponse<String>
                try {
                    val request = HttpRequests.get("https://api.2b2t.vc/$path")
                        .header("User-Agent", CollectiveClient.USER_AGENT)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .build()
                    response = HttpRequests.send(request)
                } catch (failure: RuntimeException) {
                    lastFailure = failure
                    if (Thread.currentThread().isInterrupted) throw failure
                    if (attempt == 0) {
                        try {
                            Thread.sleep(1000L)
                        } catch (interrupted: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw CompletionException(interrupted)
                        }
                    }
                    continue
                }
                return@supplyAsync when (response.statusCode()) {
                    200 -> ClientAccess.gson.fromJson(response.body(), JsonObject::class.java)
                    204 -> throw IllegalStateException("No data" + (if (playerName.isNullOrEmpty()) "" else " for player") + ".")
                    429 -> throw IllegalStateException("You are being rate limited, please try again later.")
                    else -> throw IllegalStateException("Invalid server response code: ${response.statusCode()}.")
                }
            }
            throw CompletionException("Failed to fetch data.", lastFailure)
        })
    }

    companion object {
        private val USERNAME_PATTERN: Pattern = Pattern.compile("[a-zA-Z0-9_]{1,16}")

        @JvmStatic
        fun formatDuration(totalSeconds: Int): String {
            val duration = StringBuilder()

            val days = totalSeconds / 86400
            val hours = totalSeconds / 3600 % 24
            val minutes = totalSeconds / 60 % 60
            val seconds = totalSeconds % 60
            if (days > 0) {
                duration.append(days).append(" day").append(pluralSuffix(days)).append(", ")
            }

            if (days > 0 || hours > 0) {
                duration.append(hours).append(" hour").append(pluralSuffix(hours)).append(", ")
            }

            if (days > 0 || hours > 0 || minutes > 0) {
                duration.append(minutes).append(" minute").append(pluralSuffix(minutes)).append(" and ")
            }

            duration.append(seconds).append(" second").append(pluralSuffix(seconds))
            return duration.toString()
        }

        @JvmStatic
        fun pluralSuffix(count: Int): String {
            return if (count == 1) "" else "s"
        }
    }
}
