package onevnl.ru.elytrya.commands.subcommands;

import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand implements SubCommand {

  private final BoostyClient client;

  public ReloadSubCommand(BoostyClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "reload";
  }

  @Override
  public String getPermission() {
    return "boosty.reload";
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    BoostyBridge plugin = (BoostyBridge) client.getPlugin();

    plugin.reloadConfig();

    client.reload();

    if (client.getMessageManager() != null) {
      client.getMessageManager().loadMessages();
    }

    if (plugin.getDiscordBotManager() != null) {
      plugin.getDiscordBotManager().reload();
    }

    MessageManager msg = client.getMessageManager();

    client
      .getAuthManager()
      .checkAndRefreshToken()
      .thenCompose(v -> client.getBlogManager().getBlogStats())
      .whenComplete((stats, ex) -> {
        if (ex != null || stats == null) {
          sender.sendMessage(msg.getMessage("reload_fail"));
          plugin
            .getLogger()
            .severe(
              "Failed to verify Boosty token after reload! Token might be invalid."
            );
        } else {
          sender.sendMessage(msg.getMessage("reload_success"));
          plugin
            .getLogger()
            .info(
              "Config, Discord bot settings, embeds and Boosty tokens successfully reloaded!"
            );
        }
      });
  }
}
