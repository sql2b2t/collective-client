package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import kotlin.math.min
import me.collective.client.ClientAccess
import me.collective.client.CollectiveClient
import me.collective.client.settings.JsonConfigurable
import me.collective.client.utils.network.HttpRequests

class WebhookManager : JsonConfigurable {
   @Volatile
   private var destination = Destination(null, "")

   private class Destination(val uri: URI?, val chatId: String) {
      fun telegram(): Boolean = uri != null && "api.telegram.org".equals(uri.host, ignoreCase = true)
   }

   override fun fromJson(json: JsonElement) {
      val jsonObject = json.asJsonObject
      val rawUrl = if (jsonObject.has("url") && !jsonObject.get("url").isJsonNull) {
         jsonObject.get("url").asString
      } else {
         ""
      }
      var target = parseUrl(rawUrl)
      if (target.telegram() && jsonObject.has("telegramChatId")) {
         val chatId = jsonObject.get("telegramChatId").asString
         if (chatId.isNotEmpty()) {
            validateChatId(chatId)
         }
         target = Destination(target.uri, chatId)
      }
      this.destination = target
   }

   override fun toJson(): JsonElement {
      val target = this.destination
      val json = JsonObject()
      json.addProperty("url", target.uri?.toString() ?: "")
      if (target.telegram()) {
         json.addProperty("telegramChatId", target.chatId)
      }
      return json
   }

   override fun getConfigName(): String = "webhook.json"

   val isConfigured: Boolean
      @JvmName("isConfiguredProp")
      get() {
         val target = this.destination
         return target.uri != null && (!target.telegram() || target.chatId.isNotBlank())
      }

   fun isConfigured(): Boolean = this.isConfigured

   val isTelegram: Boolean
      @JvmName("isTelegramProp")
      get() = this.destination.telegram()

   fun isTelegram(): Boolean = this.isTelegram

   var telegramChatId: String
      @JvmName("getTelegramChatIdProp")
      get() = this.destination.chatId
      @JvmName("setTelegramChatIdProp")
      set(chatId) {
         val target = this.destination
         if (!target.telegram()) {
            throw IllegalArgumentException("Chat ID is not supported for current webhook")
         }
         validateChatId(chatId)
         this.destination = Destination(target.uri, chatId)
      }

   fun getTelegramChatId(): String = this.telegramChatId

   fun setTelegramChatId(chatId: String) {
      this.telegramChatId = chatId
   }

   val destinationDescription: String
      @JvmName("getDestinationDescriptionProp")
      get() {
         val target = this.destination
         if (target.uri == null) return "No notification destination configured"
         if (target.telegram()) {
            return if (target.chatId.isBlank()) "Telegram (chat ID missing)" else "Telegram (configured)"
         }
         return "Webhook (${target.uri.host})"
      }

   fun getDestinationDescription(): String = this.destinationDescription

   fun setTelegram(botToken: String?, chatId: String?) {
      if (botToken == null || !BOT_TOKEN.matcher(botToken).matches()) {
         throw IllegalArgumentException("Invalid Telegram bot token")
      }
      validateChatId(chatId)
      this.destination = Destination(URI.create("https://api.telegram.org/bot$botToken/sendMessage"), chatId!!)
   }

   var url: String
      @JvmName("getUrlProp")
      get() = this.destination.uri?.toString() ?: ""
      @JvmName("setUrlProp")
      set(url) {
         this.destination = parseUrl(url)
      }

   fun getUrl(): String = this.url

   fun setUrl(url: String?) {
      this.destination = parseUrl(url)
   }

   internal fun createRequests(content: String?): List<HttpRequest> {
      val target = this.destination
      val uri = target.uri
      if (uri == null || (target.telegram() && target.chatId.isBlank()) || content.isNullOrEmpty()) {
         return emptyList()
      }
      val text = if (target.telegram()) ANSI_COLOR.matcher(content).replaceAll("") else content
      if (text.isEmpty()) {
         return emptyList()
      }
      val limit = if (target.telegram()) 4096 else 2000
      val requests = ArrayList<HttpRequest>()
      var start = 0
      while (start < text.length) {
         var end = min(text.length, start + limit)
         if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
            end--
         }
         val payload = JsonObject()
         if (target.telegram()) {
            payload.addProperty("chat_id", target.chatId)
            payload.addProperty("text", text.substring(start, end))
         } else {
            payload.addProperty("content", text.substring(start, end))
         }
         requests.add(
            HttpRequest.newBuilder(uri)
               .timeout(Duration.ofSeconds(30))
               .header("User-Agent", CollectiveClient.USER_AGENT)
               .header("Content-Type", "application/json; charset=UTF-8")
               .POST(BodyPublishers.ofString(payload.toString()))
               .build()
         )
         start = end
      }
      return requests.toList()
   }

   fun sendContent(content: String?) {
      val requests = this.createRequests(content)
      var delivery: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
      for (request in requests) {
         delivery = delivery.thenCompose {
            HttpRequests.sendAsync(request).thenAccept { response ->
               if (response.statusCode() < 200 || response.statusCode() >= 300) {
                  ClientAccess.logger.warn("Webhook notification rejected (HTTP {})", response.statusCode())
               } else if ("api.telegram.org".equals(request.uri().host, ignoreCase = true)) {
                  try {
                     val result = JsonParser.parseString(response.body()).asJsonObject
                     if (!result.has("ok") || !result.get("ok").asBoolean) {
                        ClientAccess.logger.warn("Telegram rejected the notification")
                     }
                  } catch (_: RuntimeException) {
                     ClientAccess.logger.warn("Telegram returned an invalid notification response")
                  }
               }
            }
         }.exceptionally { failure ->
            ClientAccess.logger.warn("Webhook notification failed ({})", failure.javaClass.simpleName)
            null
         }
      }
   }

   companion object {
      private val TELEGRAM_PATH = Pattern.compile("/bot[0-9]+:[A-Za-z0-9_-]+/sendMessage", Pattern.CASE_INSENSITIVE)
      private val BOT_TOKEN = Pattern.compile("[0-9]+:[A-Za-z0-9_-]+")
      private val CHAT_ID = Pattern.compile("-?[0-9]+|@[A-Za-z0-9_]+")
      private val ANSI_COLOR = Pattern.compile("\\x1B\\[[0-9;]*m")

      private fun parseUrl(url: String?): Destination {
         if (url == null || url.isBlank()) {
            return Destination(null, "")
         }
         try {
            var uri = URI.create(url.trim())
            if ((!"https".equals(uri.scheme, ignoreCase = true) && !"http".equals(uri.scheme, ignoreCase = true)) ||
               uri.host == null ||
               uri.userInfo != null ||
               uri.fragment != null
            ) {
               throw IllegalArgumentException()
            }
            var chatId = ""
            if ("api.telegram.org".equals(uri.host, ignoreCase = true)) {
               if (!"https".equals(uri.scheme, ignoreCase = true) ||
                  (uri.port != -1 && uri.port != 443) ||
                  !TELEGRAM_PATH.matcher(uri.path).matches()
               ) {
                  throw IllegalArgumentException()
               }
               val query = uri.rawQuery
               if (query != null) {
                  for (parameter in query.split("&")) {
                     val pair = parameter.split("=", limit = 2)
                     if (pair.size == 2 && URLDecoder.decode(pair[0], StandardCharsets.UTF_8) == "chat_id") {
                        chatId = URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                        validateChatId(chatId)
                        break
                     }
                  }
               }
               uri = URI.create("https://api.telegram.org" + uri.path)
            }
            return Destination(uri, chatId)
         } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid webhook URL")
         }
      }

      private fun validateChatId(chatId: String?) {
         if (chatId == null || !CHAT_ID.matcher(chatId).matches()) {
            throw IllegalArgumentException("Invalid Telegram chat ID")
         }
      }
   }
}
