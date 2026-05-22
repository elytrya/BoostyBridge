package onevnl.ru.elytrya.commands.subcommands;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.PendingLink;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class LinkSubCommand implements SubCommand {

  private final BoostyClient client;

  public LinkSubCommand(BoostyClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "link";
  }

  @Override
  public String getPermission() {
    return "boosty.link";
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    MessageManager msg = client.getMessageManager();

    if (!(sender instanceof Player player)) {
      sender.sendMessage(msg.getMessage("only_players"));
      return;
    }

    if (args.length < 2) {
      player.sendMessage(msg.getMessage("link_usage"));
      return;
    }

    String boostyName = String.join(
      " ",
      Arrays.copyOfRange(args, 1, args.length)
    );

    client.debug(
      "Player " +
        player.getName() +
        " trying to link Boosty account: '" +
        boostyName +
        "'"
    );

    if (!validateLink(player, boostyName, msg)) {
      return;
    }

    loadSubscriber(player, boostyName, msg);
  }

  private boolean validateLink(
    Player player,
    String boostyName,
    MessageManager msg
  ) {
    if (client.getDatabase().getBoostyName(player.getUniqueId()) != null) {
      player.sendMessage(msg.getMessage("link_already_yours"));
      return false;
    }

    if (client.getDatabase().isBoostyNameLinked(boostyName)) {
      player.sendMessage(msg.getMessage("link_already_linked"));
      return false;
    }

    return true;
  }

  private void loadSubscriber(
    Player player,
    String boostyName,
    MessageManager msg
  ) {
    player.sendMessage(msg.getMessage("link_fetching_data"));
    client
      .getBlogManager()
      .getSubscriberData(boostyName)
      .thenAccept(subData -> handleSubscriber(player, boostyName, subData, msg))
      .exceptionally(ex -> {
        client.debug("Error during linking: " + ex.getMessage());
        player.sendMessage(msg.getMessage("link_error"));
        ex.printStackTrace();
        return null;
      });
  }

  private void handleSubscriber(
    Player player,
    String boostyName,
    JsonObject subData,
    MessageManager msg
  ) {
    if (subData == null) {
      client.debug("Account '" + boostyName + "' not found in subscribers.");
      player.sendMessage(
        msg.getMessage("link_not_found").replace("%name%", boostyName)
      );
      return;
    }

    String levelName = extractLevel(subData);
    String userId = extractUserId(subData);

    FileConfiguration config = client.getPlugin().getConfig();

    if (
      handleDMVerification(player, boostyName, levelName, userId, config, msg)
    ) return;
    if (
      handleEmailVerification(
        player,
        boostyName,
        levelName,
        subData,
        config,
        msg
      )
    ) return;

    completeLink(player, boostyName, levelName, msg);
  }

  private String extractLevel(JsonObject subData) {
    if (subData.has("level") && !subData.get("level").isJsonNull()) {
      JsonObject level = subData.getAsJsonObject("level");
      if (level.has("name") && !level.get("name").isJsonNull()) {
        return level.get("name").getAsString();
      }
    }
    return "none";
  }

  private String extractUserId(JsonObject subData) {
    if (subData.has("user") && subData.getAsJsonObject("user").has("id")) {
      return subData.getAsJsonObject("user").get("id").getAsString();
    }
    if (subData.has("id")) {
      return subData.get("id").getAsString();
    }
    return null;
  }

  private boolean handleDMVerification(
    Player player,
    String boostyName,
    String levelName,
    String userId,
    FileConfiguration config,
    MessageManager msg
  ) {
    boolean useDM = config.getBoolean("dm_verification.enabled", false);
    client.debug(
      "Subscriber userId for DM: " + (userId != null ? userId : "not found")
    );

    if (!useDM || userId == null) return false;

    String code = String.format(
      "%06d",
      ThreadLocalRandom.current().nextInt(1_000_000)
    );

    client
      .getDmManager()
      .sendVerificationCode(userId, code, player.getName())
      .thenAccept(success -> {
        if (success) {
          client
            .getPendingLinks()
            .put(
              player.getUniqueId(),
              new PendingLink(boostyName, levelName, code, "dm")
            );
          player.sendMessage(msg.getMessage("link_dm_prompt"));
          client.debug(
            "DM verification code sent successfully for " + boostyName
          );
        } else {
          player.sendMessage(msg.getMessage("link_dm_error"));
        }
      });

    return true;
  }

  private boolean handleEmailVerification(
    Player player,
    String boostyName,
    String levelName,
    JsonObject subData,
    FileConfiguration config,
    MessageManager msg
  ) {
    boolean hasEmail =
      subData.has("email") &&
      !subData.get("email").isJsonNull() &&
      !subData.get("email").getAsString().isEmpty();
    boolean verifyEmail = config.getBoolean("verify_email", true);

    if (!hasEmail || !verifyEmail) return false;

    String email = subData.get("email").getAsString();
    client.debug("Subscriber has email. Adding to verification cache...");

    client
      .getPendingLinks()
      .put(
        player.getUniqueId(),
        new PendingLink(boostyName, levelName, email, "email")
      );

    player.sendMessage(msg.getMessage("link_email_prompt"));
    return true;
  }

  private void completeLink(
    Player player,
    String boostyName,
    String levelName,
    MessageManager msg
  ) {
    client.debug("No verification required. Linking immediately.");

    client
      .getDatabase()
      .saveLink(player.getUniqueId(), player.getName(), boostyName, levelName);

    player.sendMessage(
      msg.getMessage("link_success").replace("%name%", boostyName)
    );
    msg.broadcastCongratulation(player.getName(), levelName);

    ((BoostyBridge) client.getPlugin())
      .getDiscordBotManager()
      .updateUserRole(
        player.getUniqueId(),
        player.getName(),
        "none",
        levelName
      );

    client
      .getDiscordManager()
      .sendNotification(
        "subscription",
        player.getName(),
        boostyName,
        levelName
      );

    executeRewards(player, boostyName, levelName);
  }

  private void executeRewards(
    Player player,
    String boostyName,
    String levelName
  ) {
    client
      .getPlugin()
      .getServer()
      .getScheduler()
      .runTask(client.getPlugin(), () -> {
        List<String> commands = client
          .getPlugin()
          .getConfig()
          .getStringList("rewards." + levelName + ".give");

        if (commands == null || commands.isEmpty()) {
          client.debug("No rewards found for level: " + levelName);
          return;
        }

        for (String cmd : commands) {
          String finalCmd = cmd
            .replace("%player%", player.getName())
            .replace("%boosty_name%", boostyName);

          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
          client.debug("Executed reward command: " + finalCmd);
        }
      });
  }
}
