package onevnl.ru.elytrya.commands.subcommands;

import java.util.UUID;
import java.util.function.Consumer;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.BoostyUser;
import onevnl.ru.elytrya.tasks.SubscriptionSyncTask;
import onevnl.ru.elytrya.util.BoostyNameValidator;
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
    return null;
  }

  private record Target(UUID uuid, String name) {}

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
      case "setdiscord" -> handleSetDiscord(sender, args, msg);
      case "unlink" -> handleUnlink(sender, args, msg);
      case "info" -> handleInfo(sender, args, msg);
      case "forcelink" -> handleForceLink(sender, args, msg);
      case "forcesync" -> handleForceSync(sender, msg);
      default -> sender.sendMessage(msg.getMessage("unknown_command"));
    }
  }

  private void handleSetDiscord(
    CommandSender sender,
    String[] args,
    MessageManager msg
  ) {
    if (args.length < 4) {
      sender.sendMessage(msg.getMessage("admin_usage_setdiscord"));
      return;
    }

    String discordTag = args[3].trim();

    resolveTarget(sender, args[2], false, msg, target -> {
      BoostyUser user = client.getDatabase().getUser(target.uuid());
      String currentLevel = (user != null) ? user.levelName() : "none";
      String boostyName = (user != null) ? user.boostyName() : "";

      BoostyBridge plugin = (BoostyBridge) client.getPlugin();
      plugin
        .getDiscordBotManager()
        .updateUserRole(target.uuid(), target.name(), discordTag, currentLevel)
        .thenAccept(success -> {
          if (success) {
            client
              .getDatabase()
              .saveLink(
                target.uuid(),
                target.name(),
                boostyName,
                currentLevel
              );
            sender.sendMessage(
              msg
                .getMessage("admin_setdiscord_success")
                .replace("%player%", target.name())
                .replace("%discord_tag%", discordTag)
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
    });
  }

  private void handleUnlink(
    CommandSender sender,
    String[] args,
    MessageManager msg
  ) {
    if (args.length < 3) {
      sender.sendMessage(msg.getMessage("admin_usage_unlink"));
      return;
    }

    resolveTarget(sender, args[2], true, msg, target -> {
      BoostyUser user = client.getDatabase().getUser(target.uuid());

      if (user == null) {
        sender.sendMessage(
          msg
            .getMessage("admin_player_not_found")
            .replace("%player%", target.name())
        );
        return;
      }

      client.getDatabase().removeLink(target.uuid());

      sender.sendMessage(
        msg
          .getMessage("admin_unlink_success")
          .replace("%player%", target.name())
      );

      Player online = Bukkit.getPlayer(target.uuid());
      if (online != null) {
        online.sendMessage(
          msg
            .getMessage("admin_notify_unlinked")
            .replace("%admin%", sender.getName())
        );
      }
    });
  }

  private void handleInfo(
    CommandSender sender,
    String[] args,
    MessageManager msg
  ) {
    if (args.length < 3) {
      sender.sendMessage(msg.getMessage("admin_usage_info"));
      return;
    }

    resolveTarget(sender, args[2], true, msg, target -> {
      BoostyUser user = client.getDatabase().getUser(target.uuid());

      if (user == null) {
        sender.sendMessage(
          msg
            .getMessage("admin_player_not_found")
            .replace("%player%", target.name())
        );
        return;
      }

      for (String line : msg.getMessageList("info_format")) {
        sender.sendMessage(
          line
            .replace("%player%", target.name())
            .replace("%boosty%", user.boostyName())
            .replace("%level%", user.levelName())
        );
      }
    });
  }

  private void handleForceLink(
    CommandSender sender,
    String[] args,
    MessageManager msg
  ) {
    if (args.length < 5) {
      sender.sendMessage(msg.getMessage("admin_usage_forcelink"));
      return;
    }

    String boostyName = BoostyNameValidator.normalize(args[3]);
    String level = args[4];

    if (!BoostyNameValidator.isValid(boostyName)) {
      sender.sendMessage(
        msg.getMessage(
          "link_invalid_name",
          "&cНедопустимое имя Boosty. Уберите служебные символы (до " +
          BoostyNameValidator.MAX_LENGTH +
          " символов)."
        )
      );
      return;
    }

    resolveTarget(sender, args[2], false, msg, target -> {
      client
        .getDatabase()
        .saveLink(target.uuid(), target.name(), boostyName, level);

      sender.sendMessage(
        msg
          .getMessage("admin_forcelink_success")
          .replace("%boosty%", boostyName)
          .replace("%player%", target.name())
      );

      Player online = Bukkit.getPlayer(target.uuid());
      if (online != null) {
        online.sendMessage(
          msg
            .getMessage("admin_notify_forcelinked")
            .replace("%admin%", sender.getName())
            .replace("%boosty%", boostyName)
        );
      }
    });
  }

  private void handleForceSync(CommandSender sender, MessageManager msg) {
    sender.sendMessage(msg.getMessage("admin_forcesync_started"));
    sender.sendMessage(msg.getMessage("admin_forcesync_async"));

    new SubscriptionSyncTask(client).runTaskAsynchronously(client.getPlugin());
  }

  private void resolveTarget(
    CommandSender sender,
    String rawName,
    boolean requireLinked,
    MessageManager msg,
    Consumer<Target> action
  ) {
    Player online = Bukkit.getPlayerExact(rawName);
    if (online != null) {
      action.accept(new Target(online.getUniqueId(), online.getName()));
      return;
    }

    BoostyUser stored = client.getDatabase().getUserByPlayerName(rawName);
    if (stored != null) {
      action.accept(
        new Target(
          stored.uuid(),
          stored.playerName() != null ? stored.playerName() : rawName
        )
      );
      return;
    }

    if (requireLinked) {
      sender.sendMessage(
          msg
            .getMessage("admin_player_not_found")
            .replace("%player%", rawName)
        );
      return;
    }

    sender.sendMessage(
      msg.getMessage(
        "admin_resolving_player",
        "&7Игрок не найден в базе, выполняю асинхронный поиск UUID..."
      )
    );

    Bukkit.getScheduler().runTaskAsynchronously(client.getPlugin(), () -> {
      @SuppressWarnings("deprecation")
      OfflinePlayer offline = Bukkit.getOfflinePlayer(rawName);
      UUID uuid = offline.getUniqueId();
      String resolvedName = offline.getName() != null
        ? offline.getName()
        : rawName;

      Bukkit.getScheduler().runTask(client.getPlugin(), () -> {
        if (uuid == null) {
          sender.sendMessage(
          msg
            .getMessage("admin_player_not_found")
            .replace("%player%", resolvedName)
        );
          return;
        }
        action.accept(new Target(uuid, resolvedName));
      });
    });
  }
}
