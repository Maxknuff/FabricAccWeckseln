package com.fabricaccweckseln.auth;

import com.fabricaccweckseln.MCFabricAccWeckseln;
import com.fabricaccweckseln.config.AccountProfile;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MicrosoftAuthService {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/launchermc/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpClient httpClient;
    private final Gson gson;

    public MicrosoftAuthService() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public DeviceCodeSession requestDeviceCode() throws IOException, InterruptedException {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("scope", "openid offline_access XboxLive.signin");

        HttpRequest request = HttpRequest.newBuilder(URI.create(DEVICE_CODE_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(buildForm(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Device Code request failed: " + response.body());
        }

        DeviceCodeSession session = gson.fromJson(response.body(), DeviceCodeSession.class);
        if (session == null || session.device_code == null || session.user_code == null) {
            throw new IOException("Invalid device code response.");
        }
        return session;
    }

    public DeviceCodeSession pollForToken(DeviceCodeSession session) throws IOException, InterruptedException {
        long startedAt = System.currentTimeMillis();
        long expiresAt = startedAt + (long) session.expires_in * 1000L;
        while (System.currentTimeMillis() < expiresAt) {
            Thread.sleep(Math.max(session.interval, 5) * 1000L);
            Map<String, String> form = new HashMap<>();
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("client_id", CLIENT_ID);
            form.put("device_code", session.device_code);

            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(buildForm(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                TokenResponse tokenResponse = gson.fromJson(response.body(), TokenResponse.class);
                if (tokenResponse != null && tokenResponse.access_token != null) {
                    session.tokenResponse = tokenResponse;
                    return session;
                }
            }

            JsonObject errorJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String error = errorJson.has("error") ? errorJson.get("error").getAsString() : "unknown";
            if (!"authorization_pending".equals(error) && !"slow_down".equals(error)) {
                throw new IOException("Device code flow failed: " + error + " - " + (errorJson.has("error_description") ? errorJson.get("error_description").getAsString() : response.body()));
            }
            if ("slow_down".equals(error)) {
                session.interval = Math.max(session.interval + 5, 5);
            }
        }
        throw new IOException("Device code flow timed out.");
    }

    public AccountProfile finishLogin(String refreshToken, String username, String uuid) {
        AccountProfile account = new AccountProfile();
        account.username = username;
        account.uuid = uuid;
        account.refreshToken = refreshToken;
        account.lastUsed = System.currentTimeMillis();
        return account;
    }

    public MicrosoftLoginResult loginWithDeviceCode() throws IOException, InterruptedException {
        DeviceCodeSession session = requestDeviceCode();
        DeviceCodeSession result = pollForToken(session);
        if (result.tokenResponse == null || result.tokenResponse.access_token == null) {
            throw new IOException("No access token returned after device code authorization.");
        }

        String xblToken = requestXblToken(result.tokenResponse.access_token);
        String xstsToken = requestXstsToken(xblToken);
        String minecraftAccessToken = requestMinecraftAccessToken(xstsToken);
        MinecraftProfile profile = requestMinecraftProfile(minecraftAccessToken);
        return new MicrosoftLoginResult(
                finishLogin(result.tokenResponse.refresh_token, profile.name, profile.id),
                minecraftAccessToken,
                result.tokenResponse.refresh_token,
                profile
        );
    }

    public AccountProfile refreshAccount(AccountProfile profile) throws IOException, InterruptedException {
        if (profile == null || profile.refreshToken == null || profile.refreshToken.isBlank()) {
            throw new IOException("No refresh token available for this account.");
        }

        TokenResponse refreshResponse = requestRefreshToken(profile.refreshToken);
        if (refreshResponse == null || refreshResponse.access_token == null) {
            throw new IOException("Refresh token request did not return an access token.");
        }

        String xblToken = requestXblToken(refreshResponse.access_token);
        String xstsToken = requestXstsToken(xblToken);
        String minecraftAccessToken = requestMinecraftAccessToken(xstsToken);
        MinecraftProfile minecraftProfile = requestMinecraftProfile(minecraftAccessToken);

        profile.username = minecraftProfile.name;
        profile.uuid = minecraftProfile.id;
        profile.refreshToken = Objects.requireNonNullElse(refreshResponse.refresh_token, profile.refreshToken);
        profile.lastUsed = System.currentTimeMillis();
        profile.accessToken = minecraftAccessToken;
        return profile;
    }

    private TokenResponse requestRefreshToken(String refreshToken) throws IOException, InterruptedException {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        form.put("scope", "openid offline_access XboxLive.signin");

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(buildForm(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Token refresh failed: " + response.body());
        }
        return gson.fromJson(response.body(), TokenResponse.class);
    }

    private String requestXblToken(String microsoftAccessToken) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftAccessToken);
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://xboxlive.com");
        body.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder(URI.create(XBL_AUTH_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Xbox Live token request failed: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("Token").getAsString();
    }

    private String requestXstsToken(String xblToken) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        properties.add("UserTokens", userTokens);
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        HttpRequest request = HttpRequest.newBuilder(URI.create(XSTS_AUTH_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("XSTS token request failed: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject claims = json.has("Token") ? new JsonObject() : json;
        return json.get("Token").getAsString();
    }

    private String requestMinecraftAccessToken(String xstsToken) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + xstsToken);
        body.addProperty("ensureLegacyEnabled", true);

        HttpRequest request = HttpRequest.newBuilder(URI.create(MINECRAFT_LOGIN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Minecraft login failed: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("access_token")) {
            throw new IOException("Minecraft login response did not contain access_token.");
        }
        return json.get("access_token").getAsString();
    }

    private MinecraftProfile requestMinecraftProfile(String minecraftAccessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MINECRAFT_PROFILE_URL))
                .header("Authorization", "Bearer " + minecraftAccessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Minecraft profile request failed: " + response.body());
        }

        MinecraftProfile profile = gson.fromJson(response.body(), MinecraftProfile.class);
        if (profile == null || profile.id == null || profile.name == null) {
            throw new IOException("Could not parse Minecraft profile information.");
        }
        return profile;
    }

    private HttpRequest.BodyPublisher buildForm(Map<String, String> formData) {
        StringBuilder content = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (!first) {
                content.append('&');
            }
            content.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            content.append('=');
            content.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return HttpRequest.BodyPublishers.ofString(content.toString());
    }

    public static class DeviceCodeSession {
        public String device_code;
        public String user_code;
        public String verification_uri;
        public int expires_in;
        public int interval;
        public TokenResponse tokenResponse;

        public String getUserCodeDisplay() {
            return user_code == null ? "" : user_code;
        }

        public String getVerificationUri() {
            return verification_uri == null ? "https://microsoft.com/link" : verification_uri;
        }
    }

    public static class TokenResponse {
        public String access_token;
        public String refresh_token;
        public String expires_in;
        public String token_type;
        public String scope;
        public String error;
        public String error_description;
    }

    public static class MinecraftProfile {
        public String id;
        public String name;
    }

    public static class MicrosoftLoginResult {
        private final AccountProfile account;
        private final String accessToken;
        private final String refreshToken;
        private final MinecraftProfile profile;

        public MicrosoftLoginResult(AccountProfile account, String accessToken, String refreshToken, MinecraftProfile profile) {
            this.account = account;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.profile = profile;
        }

        public AccountProfile getAccount() {
            return account;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public MinecraftProfile getProfile() {
            return profile;
        }
    }
}
