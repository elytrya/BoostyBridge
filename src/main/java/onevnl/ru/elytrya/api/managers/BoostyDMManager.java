package onevnl.ru.elytrya.api.managers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import onevnl.ru.elytrya.api.BoostyClient;

public class BoostyDMManager {

    private final BoostyClient client;

    public BoostyDMManager(BoostyClient client) {
        this.client = client;
    }

    private CompletableFuture<String> createDialog(String targetUserId) {
        if (targetUserId == null || targetUserId.isEmpty()) {
            client.debug("DM: targetUserId отсутствует");
            return CompletableFuture.completedFuture(null);
        }

        String payload = "user_id=" + targetUserId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.boosty.to/v1/dialog/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + client.getAuthManager().getAccessToken())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0")
                .header("X-App", "web")
                .header("X-From-Id", client.getAuthManager().getClientId())
                .header("X-Locale", "ru_RU")
                .header("X-Currency", "RUB")
                .header("Origin", "https://boosty.to")
                .header("Referer", "https://boosty.to/app/messages")
                .header("X-Referer", "boosty.to")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        return client.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    client.debug("Create dialog => code: " + response.statusCode());
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        try {
                            JsonObject json = client.getGson().fromJson(response.body(), JsonObject.class);
                            if (json.has("id")) return json.get("id").getAsString();
                            if (json.has("dialog") && json.getAsJsonObject("dialog").has("id")) {
                                return json.getAsJsonObject("dialog").get("id").getAsString();
                            }
                            if (json.has("data") && json.getAsJsonObject("data").has("id")) {
                                return json.getAsJsonObject("data").get("id").getAsString();
                            }
                        } catch (Exception ignored) {}
                    }
                    return null;
                });
    }

    private CompletableFuture<Boolean> sendMessage(String dialogId, String text) {
        if (dialogId == null) {
            return CompletableFuture.completedFuture(false);
        }

        String escapedText = text.replace("\\", "\\\\")
                                 .replace("\"", "\\\"")
                                 .replace("\n", "\\n");

        JsonArray dataArray = new JsonArray();

        JsonObject block1 = new JsonObject();
        block1.addProperty("type", "text");
        block1.addProperty("content", "[\"" + escapedText + "\",\"unstyled\",[]]");
        block1.addProperty("modificator", "");
        dataArray.add(block1);

        JsonObject block2 = new JsonObject();
        block2.addProperty("type", "text");
        block2.addProperty("content", "");
        block2.addProperty("modificator", "BLOCK_END");
        dataArray.add(block2);

        String jsonData = client.getGson().toJson(dataArray);
        client.debug("Send message DATA: " + jsonData);

        String payload = "data=" + URLEncoder.encode(jsonData, StandardCharsets.UTF_8);

        payload += "&teaser_data=" + URLEncoder.encode("[]", StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.boosty.to/v1/dialog/" + dialogId + "/message"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + client.getAuthManager().getAccessToken())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0")
                .header("X-App", "web")
                .header("X-From-Id", client.getAuthManager().getClientId())
                .header("X-Locale", "ru_RU")
                .header("X-Currency", "RUB")
                .header("Origin", "https://boosty.to")
                .header("Referer", "https://boosty.to/app/messages?dialogId=" + dialogId)
                .header("X-Referer", "boosty.to")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        return client.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    client.debug("Send message → code: " + response.statusCode());
                    if (response.statusCode() != 200) {
                        client.debug("Send message response body: " + response.body());
                    }
                    return response.statusCode() == 200;
                });
    }

    public CompletableFuture<Boolean> sendVerificationCode(String targetUserId, String code, String playerName) {
        client.debug("DM verification → отправка кода '" + code + "' пользователю " + targetUserId + " (игрок: " + playerName + ")");

        String template = client.getPlugin().getConfig()
                .getString("dm_verification.message_template",
                        "🔐 Код подтверждения привязки Minecraft-аккаунта:\n{code}\n\nИгрок на сервере: {player}\n\nНапишите этот код в чат сервера для завершения привязки.\nЕсли вы не инициировали привязку — проигнорируйте сообщение.");

        String messageText = template
                .replace("{code}", code)
                .replace("{player}", playerName);

        return createDialog(targetUserId)
                .thenCompose(dialogId -> sendMessage(dialogId, messageText));
    }
}

// https://api.boosty.to/v1/dialog/{id}/message / отправка сооьдещния
// [{"type":"text","content":"[\"тест\\nтест\\nтест\\nтест\",\"unstyled\",[]]","modificator":""},{"type":"text","content":"","modificator":"BLOCK_END"}]
