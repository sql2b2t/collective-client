package me.collective.gui.alt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Standard Microsoft OAuth 2.0 Device Code authenticator for Minecraft.
 * Follows the official Mojang / Xbox Live authentication pipeline:
 * Microsoft OAuth -> Xbox Live (XBL) -> XSTS -> Minecraft Services -> Player Profile.
 */
public final class MicrosoftAuth {

    private static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String SCOPE = "XboxLive.signin XboxLive.offline_access";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final AtomicBoolean CANCELLED = new AtomicBoolean(false);

    private MicrosoftAuth() {}

    public static class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int expiresIn;
        public final int interval;

        public DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    public static class AuthSession {
        public final String username;
        public final UUID uuid;
        public final String mcToken;
        public final String refreshToken;

        public AuthSession(String username, UUID uuid, String mcToken, String refreshToken) {
            this.username = username;
            this.uuid = uuid;
            this.mcToken = mcToken;
            this.refreshToken = refreshToken;
        }
    }

    public static void cancel() {
        CANCELLED.set(true);
    }

    /**
     * Step 1: Request Microsoft OAuth Device Code
     */
    public static CompletableFuture<DeviceCode> requestDeviceCode() {
        CANCELLED.set(false);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                        + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new RuntimeException("Failed to request device code: " + res.body());
                }

                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                String devCode = json.get("device_code").getAsString();
                String userCode = json.get("user_code").getAsString();
                String uri = json.has("verification_uri") ? json.get("verification_uri").getAsString() : "https://www.microsoft.com/link";
                int exp = json.has("expires_in") ? json.get("expires_in").getAsInt() : 900;
                int intv = json.has("interval") ? json.get("interval").getAsInt() : 5;

                // Copy code to user's clipboard automatically
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.keyboardHandler != null) {
                        mc.keyboardHandler.setClipboard(userCode);
                    }
                } catch (Exception ignored) {}

                // Open browser
                try {
                    com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(uri));
                } catch (Exception ignored) {}

                return new DeviceCode(devCode, userCode, uri, exp, intv);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Step 2: Poll Microsoft token, then authenticate with Xbox and Minecraft
     */
    public static CompletableFuture<AuthSession> pollAndAuthenticate(DeviceCode dc, Consumer<String> statusUpdate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long deadline = System.currentTimeMillis() + (dc.expiresIn * 1000L);
                String msAccessToken = null;
                String msRefreshToken = null;

                while (System.currentTimeMillis() < deadline) {
                    if (CANCELLED.get()) {
                        throw new RuntimeException("Cancelled by user");
                    }

                    Thread.sleep(Math.max(3000, dc.interval * 1000L));

                    if (CANCELLED.get()) {
                        throw new RuntimeException("Cancelled by user");
                    }

                    String pollBody = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                            + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                            + "&device_code=" + URLEncoder.encode(dc.deviceCode, StandardCharsets.UTF_8);

                    HttpRequest pollReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(pollBody))
                            .build();

                    HttpResponse<String> pollRes = HTTP.send(pollReq, HttpResponse.BodyHandlers.ofString());
                    if (pollRes.statusCode() == 200) {
                        JsonObject tokenJson = JsonParser.parseString(pollRes.body()).getAsJsonObject();
                        msAccessToken = tokenJson.get("access_token").getAsString();
                        if (tokenJson.has("refresh_token")) {
                            msRefreshToken = tokenJson.get("refresh_token").getAsString();
                        }
                        break;
                    }

                    JsonObject errJson = JsonParser.parseString(pollRes.body()).getAsJsonObject();
                    String err = errJson.has("error") ? errJson.get("error").getAsString() : "";
                    if (!"authorization_pending".equals(err)) {
                        throw new RuntimeException("Microsoft auth error: " + err);
                    }
                }

                if (msAccessToken == null) {
                    throw new RuntimeException("Device code expired");
                }

                statusUpdate.accept("Connecting to Xbox Live...");
                return finishMinecraftAuth(msAccessToken, msRefreshToken, statusUpdate);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Refresh existing Microsoft session using saved refresh_token
     */
    public static CompletableFuture<AuthSession> refreshSession(String refreshToken, Consumer<String> statusUpdate) {
        CANCELLED.set(false);
        return CompletableFuture.supplyAsync(() -> {
            try {
                statusUpdate.accept("Refreshing Microsoft token...");
                String body = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                        + "&grant_type=refresh_token"
                        + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new RuntimeException("Refresh failed, please log in again");
                }

                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                String newAccess = json.get("access_token").getAsString();
                String newRefresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;

                return finishMinecraftAuth(newAccess, newRefresh, statusUpdate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static AuthSession finishMinecraftAuth(String msAccessToken, String msRefreshToken, Consumer<String> status) throws Exception {
        // Step 1: Xbox Live Authentication (XBL)
        status.accept("Authenticating with Xbox Live...");
        JsonObject xblReq = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        xblReq.add("Properties", xblProps);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(xblReq.toString()))
                .build();

        HttpResponse<String> res1 = HTTP.send(req1, HttpResponse.BodyHandlers.ofString());
        if (res1.statusCode() != 200) throw new RuntimeException("Xbox Live auth failed");

        JsonObject xblJson = JsonParser.parseString(res1.body()).getAsJsonObject();
        String xblToken = xblJson.get("Token").getAsString();
        String uhs = xblJson.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // Step 2: XSTS Authentication
        status.accept("Authorizing with XSTS...");
        JsonObject xstsReq = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");

        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(xstsReq.toString()))
                .build();

        HttpResponse<String> res2 = HTTP.send(req2, HttpResponse.BodyHandlers.ofString());
        if (res2.statusCode() != 200) throw new RuntimeException("XSTS auth failed");

        JsonObject xstsJson = JsonParser.parseString(res2.body()).getAsJsonObject();
        String xstsToken = xstsJson.get("Token").getAsString();

        // Step 3: Login with Xbox to Minecraft Services
        status.accept("Authenticating with Minecraft...");
        JsonObject mcLoginReq = new JsonObject();
        mcLoginReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        HttpRequest req3 = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mcLoginReq.toString()))
                .build();

        HttpResponse<String> res3 = HTTP.send(req3, HttpResponse.BodyHandlers.ofString());
        if (res3.statusCode() != 200) throw new RuntimeException("Minecraft login failed");

        JsonObject mcLoginJson = JsonParser.parseString(res3.body()).getAsJsonObject();
        String mcAccessToken = mcLoginJson.get("access_token").getAsString();

        // Step 4: Fetch Player Profile
        status.accept("Retrieving Minecraft profile...");
        HttpRequest req4 = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcAccessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> res4 = HTTP.send(req4, HttpResponse.BodyHandlers.ofString());
        if (res4.statusCode() != 200) throw new RuntimeException("Failed to get profile: do you own Minecraft?");

        JsonObject profJson = JsonParser.parseString(res4.body()).getAsJsonObject();
        String username = profJson.get("name").getAsString();
        String rawUuid = profJson.get("id").getAsString();
        UUID uuid = parseUndashedUuid(rawUuid);

        status.accept("Logged in as " + username);
        return new AuthSession(username, uuid, mcAccessToken, msRefreshToken);
    }

    private static UUID parseUndashedUuid(String s) {
        if (s.length() == 32) {
            String formatted = s.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5");
            return UUID.fromString(formatted);
        }
        return UUID.fromString(s);
    }
}