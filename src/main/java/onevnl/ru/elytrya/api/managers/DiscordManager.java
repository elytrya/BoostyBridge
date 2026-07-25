package onevnl.ru.elytrya.api.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.util.DiscordTextSanitizer;
import org.bukkit.configuration.ConfigurationSection;

public class DiscordManager {

  private final BoostyClient client;
  private final HttpClient httpClient;

  public DiscordManager(BoostyClient client) {
    this.client = client;
    this.httpClient = HttpClient.newHttpClient();
  }

  public void sendNotification(
    String type,
    String playerName,
    String boostyName,
    String levelName
  ) {
    ConfigurationSection config = client
      .getPlugin()
      .getConfig()
      .getConfigurationSection("discord");
    if (config == null || !config.getBoolean("enabled", false)) return;

    String webhookUrl = config.getString("webhook_url", "");
    if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK")) {
      return;
    }

    ConfigurationSection event = config.getConfigurationSection(type);
    if (event == null || !event.getBoolean("enabled", true)) return;

    String safePlayer = DiscordTextSanitizer.value(playerName);
    String safeBoostyName = DiscordTextSanitizer.value(
      boostyName != null && !boostyName.isEmpty() ? boostyName : "N/A"
    );
    String safeLevel = DiscordTextSanitizer.value(
      levelName != null && !levelName.isEmpty() ? levelName : "none"
    );

    String description = applyPlaceholders(
      event.getString("message", ""),
      safePlayer,
      safeBoostyName,
      safeLevel
    );
    String title = applyPlaceholders(
      event.getString("title", "Notification"),
      safePlayer,
      safeBoostyName,
      safeLevel
    );

    JsonObject embed = new JsonObject();
    embed.addProperty(
      "title",
      DiscordTextSanitizer.truncate(title, DiscordTextSanitizer.TITLE_LIMIT)
    );
    embed.addProperty(
      "description",
      DiscordTextSanitizer.truncate(
        description,
        DiscordTextSanitizer.DESCRIPTION_LIMIT
      )
    );

    try {
      String colorHex = config
        .getString("embed_color", "#FFB6C1")
        .replace("#", "");
      embed.addProperty("color", Integer.parseInt(colorHex, 16));
    } catch (Exception e) {
      embed.addProperty("color", 16758465);
    }

    JsonArray embeds = new JsonArray();
    embeds.add(embed);

    JsonObject payload = new JsonObject();
    payload.add("embeds", embeds);

    JsonObject allowedMentions = new JsonObject();
    allowedMentions.add("parse", new JsonArray());
    payload.add("allowed_mentions", allowedMentions);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(webhookUrl))
      .header("Content-Type", "application/json")
      .POST(
        HttpRequest.BodyPublishers.ofString(
          payload.toString(),
          StandardCharsets.UTF_8
        )
      )
      .build();

    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
  }

  private String applyPlaceholders(
    String template,
    String playerName,
    String boostyName,
    String levelName
  ) {
    if (template == null) return "";
    return template
      .replace("{player}", playerName)
      .replace("{boosty_name}", boostyName)
      .replace("{level}", levelName);
  }
}
