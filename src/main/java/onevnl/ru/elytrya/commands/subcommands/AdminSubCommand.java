package onevnl.ru.elytrya.commands.subcommands;

import java.util.UUID;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

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
    return null;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    MessageManager msg = client.getMessageManager();

    if (args.length < 2) {
      if (sender.hasPermission("boosty.admin.*")) {
        sender.sendMessage(msg.getMessage("help_menu_admin"));
      } else {
        sender.sendMessage(msg.getMessage("no_permission"));
      }
      return;
    }

    String action = args[1].toLowerCase();
    String requiredPermission = action.equals("reload")
      ? "boosty.reload"
      : "boosty.admin." + action;

    if (!sender.hasPermission(requiredPermission)) {
      sender.sendMessage(msg.getMessage("no_permission"));
      return;
    }

    if (
      action.equals("setdiscord") &&
      !client.getPlugin().getConfig().getBoolean("discord.bot.enabled", false)
    ) {
      sender.sendMessage(msg.getMessage("unknown_command"));
      return;
    }

    switch (action) {
      case "setdiscord" -> {
        if (args.length < 4) {
          sender.sendMessage(msg.getMessage("admin_usage_setdiscord"));
          return;
        }
        String targetName = args[2];
        String discordTag = args[3].trim();

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        BoostyUser user = client.getDatabase().getUser(target.getUniqueId());

        String currentLevel = (user != null) ? user.levelName() : "none";
        String boostyName = (user != null) ? user.boostyName() : "";

        BoostyBridge plugin = (BoostyBridge) client.getPlugin();
        plugin
          .getDiscordBotManager()
          .updateUserRole(
            target.getUniqueId(),
            targetName,
            discordTag,
            currentLevel
          )
          .thenAccept(success -> {
            if (success) {
              client
                .getDatabase()
                .saveLink(
                  target.getUniqueId(),
                  targetName,
                  boostyName,
                  currentLevel
                );
              sender.sendMessage(
                msg
                  .getMessage("admin_setdiscord_success")
                  .replace("%player%", targetName)
                  .replace("%discord%", discordTag)
              );
            } else {
              sender.sendMessage(
                msg
                  .getMessage("discord_not_found")
                  .replace("%discord_tag%", discordTag)
              );
            }
          });
      }
      case "unlink" -> {
        if (args.length < 3) {
          sender.sendMessage(msg.getMessage("admin_usage_unlink"));
          return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        BoostyUser user = client.getDatabase().getUser(target.getUniqueId());

        if (user == null) {
          sender.sendMessage(msg.getMessage("admin_player_not_found"));
          return;
        }

        client.getDatabase().removeLink(target.getUniqueId());

        sender.sendMessage(
          msg
            .getMessage("admin_unlink_success")
            .replace("%player%", target.getName())
        );

        if (target.isOnline() && target.getPlayer() != null) {
          target
            .getPlayer()
            .sendMessage(
              msg
                .getMessage("admin_notify_unlinked")
                .replace("%admin%", sender.getName())
            );
        }
      }
      case "info" -> {
        if (args.length < 3) {
          sender.sendMessage(msg.getMessage("admin_usage_info"));
          return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        BoostyUser user = client.getDatabase().getUser(target.getUniqueId());

        if (user == null) {
          sender.sendMessage(msg.getMessage("admin_player_not_found"));
          return;
        }

        for (String line : msg.getMessageList("info_format")) {
          sender.sendMessage(
            line
              .replace("%player%", target.getName())
              .replace("%boosty%", user.boostyName())
              .replace("%level%", user.levelName())
          );
        }
      }
      case "forcelink" -> {
        if (args.length < 5) {
          sender.sendMessage(msg.getMessage("admin_usage_forcelink"));
          return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        String bName = args[3];
        String level = args[4];

        client
          .getDatabase()
          .saveLink(target.getUniqueId(), target.getName(), bName, level);

        sender.sendMessage(
          msg
            .getMessage("admin_forcelink_success")
            .replace("%boosty%", bName)
            .replace("%player%", target.getName())
        );

        if (target.isOnline() && target.getPlayer() != null) {
          target
            .getPlayer()
            .sendMessage(
              msg
                .getMessage("admin_notify_forcelinked")
                .replace("%admin%", sender.getName())
                .replace("%boosty%", bName)
            );
        }
      }
      case "forcesync" -> {
        sender.sendMessage(msg.getMessage("admin_forcesync_started"));
        sender.sendMessage(msg.getMessage("admin_forcesync_async"));

        Bukkit.getScheduler().runTaskAsynchronously(client.getPlugin(), () -> {
          sender.sendMessage(msg.getMessage("admin_forcesync_success"));
        });
      }
      default -> sender.sendMessage(msg.getMessage("unknown_command"));
    }
  }
}
