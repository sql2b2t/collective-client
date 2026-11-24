package me.collective.client.managers;

import me.collective.client.utils.network.HttpRequests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.collective.client.ClientAccess;
import me.collective.client.CollectiveClient;
import me.collective.client.settings.JsonConfigurable;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class WebhookManager implements JsonConfigurable {
   private static final Pattern TELEGRAM_PATH = Pattern.compile("/bot[0-9]+:[A-Za-z0-9_-]+/sendMessage", Pattern.CASE_INSENSITIVE);
   private static final Pattern BOT_TOKEN = Pattern.compile("[0-9]+:[A-Za-z0-9_-]+");
   private static final Pattern CHAT_ID = Pattern.compile("-?[0-9]+|@[A-Za-z0-9_]+");
   private static final Pattern ANSI_COLOR = Pattern.compile("\\x1B\\[[0-9;]*m");
   private volatile Destination destination = new Destination(null, "");

   private record Destination(URI uri, String chatId) {
      boolean telegram() {
         return uri != null && "api.telegram.org".equalsIgnoreCase(uri.getHost());
      }
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject object = json.getAsJsonObject();
      Destination target = parseUrl(object.has("url") && !object.get("url").isJsonNull() ? object.get("url").getAsString() : "");
      if (target.telegram() && object.has("telegramChatId")) {
         String chatId = object.get("telegramChatId").getAsString();
         if (!chatId.isEmpty()) validateChatId(chatId);
         target = new Destination(target.uri(), chatId);
      }
      this.destination = target;
   }

   @Override
   public JsonElement toJson() {
      Destination target = this.destination;
      JsonObject json = new JsonObject();
      json.addProperty("url", target.uri() == null ? "" : target.uri().toString());
      if (target.telegram()) json.addProperty("telegramChatId", target.chatId());
      return json;
   }

   @Override
   public String getConfigName() {
      return "webhook.json";
   }

   public boolean isConfigured() {
      Destination target = this.destination;
      return target.uri() != null && (!target.telegram() || !target.chatId().isBlank());
   }

   public boolean isTelegram() {
      return this.destination.telegram();
   }

   public String getTelegramChatId() {
      return this.destination.chatId();
   }

   public void setTelegramChatId(String chatId) {
      Destination target = this.destination;
      if (!target.telegram()) {
         throw new IllegalArgumentException("Chat ID is not supported for current webhook");
      }
      validateChatId(chatId);
      this.destination = new Destination(target.uri(), chatId);
   }

   public String getDestinationDescription() {
      Destination target = this.destination;
      if (target.uri() == null) return "No notification destination configured";
      if (target.telegram()) return target.chatId().isBlank() ? "Telegram (chat ID missing)" : "Telegram (configured)";
      return "Webhook (" + target.uri().getHost() + ")";
   }

   public void setTelegram(String botToken, String chatId) {
      if (botToken == null || !BOT_TOKEN.matcher(botToken).matches()) {
         throw new IllegalArgumentException("Invalid Telegram bot token");
      }
      validateChatId(chatId);
      this.destination = new Destination(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"), chatId);
   }

   public void setUrl(String url) {
      this.destination = parseUrl(url);
   }

   private static Destination parseUrl(String url) {
      if (url == null || url.isBlank()) return new Destination(null, "");
      try {
         URI uri = URI.create(url.trim());
         if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException();
         }
         String chatId = "";
         if ("api.telegram.org".equalsIgnoreCase(uri.getHost())) {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getPort() != -1 && uri.getPort() != 443
               || !TELEGRAM_PATH.matcher(uri.getPath()).matches()) throw new IllegalArgumentException();
            String query = uri.getRawQuery();
            if (query != null) {
               for (String parameter : query.split("&")) {
                  String[] pair = parameter.split("=", 2);
                  if (pair.length == 2 && URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals("chat_id")) {
                     chatId = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                     validateChatId(chatId);
                     break;
                  }
               }
            }
            uri = URI.create("https://api.telegram.org" + uri.getPath());
         }
         return new Destination(uri, chatId);
      } catch (IllegalArgumentException invalid) {

         throw new IllegalArgumentException("Invalid webhook URL");
      }
   }

   public String getUrl() {
      URI uri = this.destination.uri();
      return uri == null ? "" : uri.toString();
   }

   private static void validateChatId(String chatId) {
      if (chatId == null || !CHAT_ID.matcher(chatId).matches()) {
         throw new IllegalArgumentException("Invalid Telegram chat ID");
      }
   }

   List<HttpRequest> createRequests(String content) {
      Destination target = this.destination;
      if (target.uri() == null || target.telegram() && target.chatId().isBlank() || content == null || content.isEmpty()) return List.of();
      String text = target.telegram() ? ANSI_COLOR.matcher(content).replaceAll("") : content;
      if (text.isEmpty()) return List.of();
      int limit = target.telegram() ? 4096 : 2000;
      List<HttpRequest> requests = new ArrayList<>();
      for (int start = 0; start < text.length();) {
         int end = Math.min(text.length(), start + limit);
         if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
         JsonObject payload = new JsonObject();
         if (target.telegram()) {
            payload.addProperty("chat_id", target.chatId());
            payload.addProperty("text", text.substring(start, end));
         } else {
            payload.addProperty("content", text.substring(start, end));
         }
         requests.add(HttpRequest.newBuilder(target.uri())
            .timeout(java.time.Duration.ofSeconds(30))
            .header("User-Agent", CollectiveClient.USER_AGENT)
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(BodyPublishers.ofString(payload.toString())).build());
         start = end;
      }
      return List.copyOf(requests);
   }

   public void sendContent(String content) {
      List<HttpRequest> requests = this.createRequests(content);
      CompletableFuture<Void> delivery = CompletableFuture.completedFuture(null);
      for (HttpRequest request : requests) {
         delivery = delivery.thenCompose(ignored -> HttpRequests.sendAsync(request).thenAccept(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
               ClientAccess.logger.warn("Webhook notification rejected (HTTP {})", response.statusCode());
            } else if ("api.telegram.org".equalsIgnoreCase(request.uri().getHost())) {
               try {
                  JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                  if (!result.has("ok") || !result.get("ok").getAsBoolean()) {
                     ClientAccess.logger.warn("Telegram rejected the notification");
                  }
               } catch (RuntimeException invalidResponse) {
                  ClientAccess.logger.warn("Telegram returned an invalid notification response");
               }
            }
         })).exceptionally(failure -> {
            ClientAccess.logger.warn("Webhook notification failed ({})", failure.getClass().getSimpleName());
            return null;
         });
      }
   }
}
