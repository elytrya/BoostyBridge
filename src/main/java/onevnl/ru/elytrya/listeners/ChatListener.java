package onevnl.ru.elytrya.listeners;

import java.util.List;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.PendingLink;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

  private final BoostyClient client;

  public ChatListener(BoostyClient client) {
    this.client = client;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();

    if (!client.getPendingLinks().containsKey(player.getUniqueId())) {
      return;
    }

    event.setCancelled(true);
    event.getRecipients().clear();

    PendingLink pending = client.getPendingLinks().remove(player.getUniqueId());

    String rawInput = event.getMessage();
    String input = ChatColor.stripColor(rawInput).trim();
    MessageManager msg = client.getMessageManager();

    BoostyBridge plugin = (BoostyBridge) client.getPlugin();

    if (input.equalsIgnoreCase(pending.verificationValue())) {
      client.debug("Verification successful!");
      client
        .getDatabase()
        .saveLink(
          player.getUniqueId(),
          player.getName(),
          pending.boostyName(),
          pending.levelName()
        );
      player.sendMessage(
        msg.getMessage("link_success").replace("%name%", pending.boostyName())
      );
      msg.broadcastCongratulation(player.getName(), pending.levelName());

      plugin
        .getDiscordBotManager()
        .updateUserRole(
          player.getUniqueId(),
          player.getName(),
          "none",
          pending.levelName()
        );

      executeRewards(player, pending.boostyName(), pending.levelName());
    } else {
      client.debug("Verification failed.");
      player.sendMessage(msg.getMessage("link_email_fail"));
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    client.getPendingLinks().remove(event.getPlayer().getUniqueId());
  }

  private void executeRewards(
    Player player,
    String boostyName,
    String levelName
  ) {
    List<String> commands = client
      .getPlugin()
      .getConfig()
      .getStringList("rewards." + levelName + ".give");

    if (commands == null || commands.isEmpty()) {
      client.debug("No rewards found for level: " + levelName);
      return;
    }

    Bukkit.getScheduler().runTask(client.getPlugin(), () -> {
      for (String cmd : commands) {
        String finalCmd = cmd
          .replace("%player%", player.getName())
          .replace("%boosty_name%", boostyName);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
        client.debug("Executed reward command on main thread: " + finalCmd);
      }
    });
  }
}
