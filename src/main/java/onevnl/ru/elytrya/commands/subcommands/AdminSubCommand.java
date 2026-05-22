package onevnl.ru.elytrya.commands.subcommands;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminSubCommand implements SubCommand {

  private final BoostyClient client;

  public AdminSubCommand(BoostyClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "admin";
  }

  @Override
  public String getPermission() {
    return "boosty.admin";
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    MessageManager msg = client.getMessageManager();
    if (args.length < 2) {
      sender.sendMessage(msg.getMessage("error_use_admin"));
      return;
    }

    String action = args[1].toLowerCase();

    if (action.equals("setdiscord")) {
      if (args.length < 4) {
        sender.sendMessage(msg.getMessage("admin_setdiscord_usage"));
        return;
      }
      String targetName = args[2];
      String newDiscord = args[3];

      OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
      client.getDatabase().setDiscordUser(target.getUniqueId(), newDiscord);
      sender.sendMessage(
        msg
          .getMessage("admin_setdiscord_success")
          .replace("%player%", targetName)
          .replace("%discord%", newDiscord)
      );

      BoostyUser user = client.getDatabase().getUser(target.getUniqueId());
      String currentLevel = (user != null) ? user.levelName() : "none";
      ((BoostyBridge) client.getPlugin())
        .getDiscordBotManager()
        .updateUserRole(target.getUniqueId(), targetName, "none", currentLevel);
      return;
    }

    if (action.equals("unlink")) {
      if (args.length < 3) {
        sender.sendMessage(msg.getMessage("error_use_unlink"));
        return;
      }
      String targetName = args[2];
      boolean silent = args.length > 3 && args[3].equalsIgnoreCase("-s");

      BoostyUser user = findUser(targetName);
      if (user == null) {
        sender.sendMessage(
          msg
            .getMessage("admin_unlink_not_linked")
            .replace("%player%", targetName)
        );
        return;
      }

      String notifyType = silent ? "unsubscription" : "admin_unlink";
      client
        .getDiscordManager()
        .sendNotification(
          notifyType,
          user.playerName(),
          user.boostyName(),
          user.levelName()
        );

      ((BoostyBridge) client.getPlugin())
        .getDiscordBotManager()
        .removeUserRole(user.uuid(), user.playerName(), user.levelName());

      client.getDatabase().removeLink(user.uuid());
      takeRewards(user.playerName(), user.boostyName(), user.levelName());
      notifyUnlink(user.uuid(), sender.getName());
      sender.sendMessage(
        msg.getMessage("admin_unlink_success").replace("%player%", targetName)
      );
      return;
    }

    if (action.equals("info")) {
      if (args.length < 3) {
        sender.sendMessage(msg.getMessage("error_use_info"));
        return;
      }
      String targetName = args[2];
      BoostyUser user = findUser(targetName);
      if (user == null) {
        sender.sendMessage(
          msg
            .getMessage("admin_info_not_linked")
            .replace("%player%", targetName)
        );
        return;
      }
      sender.sendMessage(
        msg
          .getMessage("admin_info_linked")
          .replace("%player%", user.playerName())
          .replace("%boosty%", user.boostyName())
          .replace("%level%", user.levelName())
      );
      return;
    }

    if (action.equals("forcelink")) {
      if (args.length < 4) {
        sender.sendMessage(msg.getMessage("error_use_forcelink"));
        return;
      }
      String targetName = args[2];
      String boostyName = args[3];
      boolean silent = args.length > 4 && args[4].equalsIgnoreCase("-s");

      Player target = Bukkit.getPlayer(targetName);
      if (target == null || !target.isOnline()) {
        sender.sendMessage(msg.getMessage("admin_offline_player"));
        return;
      }

      sender.sendMessage(msg.getMessage("admin_fetching_data"));
      client
        .getBlogManager()
        .getSubscriberData(boostyName)
        .thenAccept(subData -> {
          String levelName = extractLevel(subData);

          BoostyUser oldUser = findUser(targetName);
          String oldLevel = (oldUser != null) ? oldUser.levelName() : "none";

          client
            .getDatabase()
            .saveLink(target.getUniqueId(), targetName, boostyName, levelName);

          ((BoostyBridge) client.getPlugin())
            .getDiscordBotManager()
            .updateUserRole(
              target.getUniqueId(),
              targetName,
              oldLevel,
              levelName
            );

          String notifyType = silent ? "subscription" : "admin_forcelink";
          client
            .getDiscordManager()
            .sendNotification(notifyType, targetName, boostyName, levelName);

          executeRewards(targetName, boostyName, levelName);
          sender.sendMessage(
            msg
              .getMessage("admin_forcelink_success")
              .replace("%boosty%", boostyName)
              .replace("%player%", targetName)
          );
        })
        .exceptionally(ex -> {
          sender.sendMessage(
            msg
              .getMessage("admin_link_error")
              .replace("%error%", ex.getMessage())
          );
          return null;
        });
      return;
    }

    if (action.equals("forcesync")) {
      sender.sendMessage(msg.getMessage("admin_forcesync_started"));
      new onevnl.ru.elytrya.tasks.SubscriptionSyncTask(
        client
      ).runTaskAsynchronously(client.getPlugin());
      sender.sendMessage(msg.getMessage("admin_forcesync_async"));
      return;
    }

    sender.sendMessage(msg.getMessage("unknown_command"));
  }

  private BoostyUser findUser(String name) {
    return client
      .getDatabase()
      .getAllUsers()
      .stream()
      .filter(u -> u.playerName().equalsIgnoreCase(name))
      .findFirst()
      .orElse(null);
  }

  private void notifyUnlink(UUID uuid, String admin) {
    Player p = Bukkit.getPlayer(uuid);
    if (p != null && p.isOnline()) {
      p.sendMessage(
        client
          .getMessageManager()
          .getMessage("admin_notify_unlinked")
          .replace("%admin%", admin)
      );
    }
  }

  private String extractLevel(JsonObject subData) {
    if (
      subData != null &&
      subData.has("level") &&
      !subData.get("level").isJsonNull()
    ) {
      JsonObject levelObj = subData.getAsJsonObject("level");
      if (levelObj.has("name") && !levelObj.get("name").isJsonNull()) {
        return levelObj.get("name").getAsString();
      }
    }
    return "none";
  }

  private void executeRewards(
    String playerName,
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
        if (commands == null) return;
        for (String cmd : commands) {
          Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            cmd
              .replace("%player%", playerName)
              .replace("%boosty_name%", boostyName)
          );
        }
      });
  }

  private void takeRewards(
    String playerName,
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
          .getStringList("rewards." + levelName + ".take");
        if (commands == null) return;
        for (String cmd : commands) {
          Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            cmd
              .replace("%player%", playerName)
              .replace("%boosty_name%", boostyName)
          );
        }
      });
  }
}
