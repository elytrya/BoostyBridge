package onevnl.ru.elytrya.commands.subcommands;

import java.util.UUID;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DiscordSubCommand implements SubCommand {

  private final BoostyClient client;

  public DiscordSubCommand(BoostyClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "discord";
  }

  @Override
  public String getPermission() {
    return "boosty.discord";
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    MessageManager msg = client.getMessageManager();

    if (!(sender instanceof Player player)) {
      sender.sendMessage(msg.getMessage("only_players"));
      return;
    }

    if (args.length < 2) {
      player.sendMessage(msg.getMessage("discord_usage"));
      return;
    }

    UUID uuid = player.getUniqueId();
    BoostyUser user = client.getDatabase().getUser(uuid);

    // Надежная проверка по базе данных: если запись есть и аккаунт уже находится на синхронизации
    if (
      user != null && user.boostyName() != null && !user.boostyName().isEmpty()
    ) {
      if (player.hasMetadata("boosty_discord_linked")) {
        player.sendMessage(msg.getMessage("discord_already_linked"));
        return;
      }
    }

    String discordTag = args[1].trim();
    String playerName = player.getName();
    String currentLevel = (user != null) ? user.levelName() : "none";
    String boostyName = (user != null) ? user.boostyName() : "Не привязан";

    player.sendMessage(
      msg.getMessage("discord_checking").replace("%discord_tag%", discordTag)
    );

    BoostyBridge plugin = (BoostyBridge) client.getPlugin();
    plugin
      .getDiscordBotManager()
      .updateUserRole(uuid, playerName, discordTag, currentLevel)
      .thenAccept(success -> {
        if (success) {
          client
            .getDatabase()
            .saveLink(uuid, playerName, boostyName, currentLevel);

          player.setMetadata(
            "boosty_discord_linked",
            new org.bukkit.metadata.FixedMetadataValue(plugin, true)
          );

          player.sendMessage(
            msg
              .getMessage("discord_success")
              .replace("%discord_tag%", discordTag)
          );
        } else {
          player.sendMessage(
            msg
              .getMessage("discord_not_found")
              .replace("%discord_tag%", discordTag)
          );
        }
      })
      .exceptionally(ex -> {
        player.sendMessage(msg.getMessage("discord_error"));
        client
          .getPlugin()
          .getLogger()
          .severe("Ошибка в /boosty discord: " + ex.getMessage());
        return null;
      });
  }
}
