package onevnl.ru.elytrya.commands.subcommands;

import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.DiscordBotManager;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoSubCommand implements SubCommand {

  private final BoostyClient client;

  public InfoSubCommand(BoostyClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "info";
  }

  @Override
  public String getPermission() {
    return "boosty.info";
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    MessageManager msg = client.getMessageManager();

    if (!(sender instanceof Player player)) {
      sender.sendMessage(msg.getMessage("only_players"));
      return;
    }

    BoostyUser user = client.getDatabase().getUser(player.getUniqueId());

    if (user == null) {
      player.sendMessage(msg.getMessage("info_not_linked"));
      return;
    }

    player.sendMessage(
      msg
        .getMessage("info_linked")
        .replace("%boosty%", user.boostyName())
        .replace("%level%", user.levelName())
        .replace("%player%", player.getName())
    );

    sendDiscordStatus(player, user, msg);
  }

  private void sendDiscordStatus(
    Player player,
    BoostyUser user,
    MessageManager msg
  ) {
    DiscordBotManager bot =
      ((BoostyBridge) client.getPlugin()).getDiscordBotManager();

    if (bot == null || !bot.isEnabled()) return;

    String discordName = client
      .getDatabase()
      .getDiscordUser(player.getUniqueId());

    if (discordName != null && !discordName.isEmpty()) {
      player.sendMessage(
        msg
          .getMessage(
            "info_discord_linked",
            "&fDiscord: &#FFB6C1%discord_tag%"
          )
          .replace("%discord_tag%", discordName)
      );
      return;
    }

    player.sendMessage(
      msg.getMessage(
        "info_discord_not_linked",
        "&fDiscord: &7\u043d\u0435 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d &f- &#FFB6C1/boosty discord <\u044e\u0437\u0435\u0440\u043d\u0435\u0439\u043c>"
      )
    );

    if (bot.hasRoleForLevel(user.levelName())) {
      player.sendMessage(
        msg
          .getMessage(
            "discord_prompt_link",
            "&f\u0417\u0430 \u0443\u0440\u043e\u0432\u0435\u043d\u044c &#FFB6C1%level% &f\u0432\u044b\u0434\u0430\u0451\u0442\u0441\u044f \u0440\u043e\u043b\u044c \u0432 Discord. \u041f\u0440\u0438\u0432\u044f\u0436\u0438\u0442\u0435 Discord: &#FFB6C1/boosty discord <\u0432\u0430\u0448_\u044e\u0437\u0435\u0440\u043d\u0435\u0439\u043c>"
          )
          .replace("%level%", user.levelName())
      );
    }
  }
}
