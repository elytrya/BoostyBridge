package onevnl.ru.elytrya.tasks;

import java.util.List;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class SubscriptionSyncTask extends BukkitRunnable {

  private final BoostyClient client;

  public SubscriptionSyncTask(BoostyClient client) {
    this.client = client;
  }

  @Override
  public void run() {
    client.debug("Starting subscription sync task...");

    client
      .getBlogManager()
      .getAllSubscribersMap()
      .thenAccept(subscribers -> {
        List<BoostyUser> dbUsers = client.getDatabase().getAllUsers();
        BoostyBridge plugin = (BoostyBridge) client.getPlugin();

        for (BoostyUser user : dbUsers) {
          String boostyName = user.boostyName().toLowerCase();
          String currentLevel = user.levelName();

          if (!subscribers.containsKey(boostyName)) {
            client.debug(
              "player " + user.playerName() + " lost their subscription!"
            );
            takeRewards(user.playerName(), user.boostyName(), currentLevel);

            plugin
              .getDiscordBotManager()
              .removeUserRole(user.uuid(), user.playerName(), currentLevel);

            client
              .getDiscordManager()
              .sendNotification(
                "unsubscription",
                user.playerName(),
                user.boostyName(),
                currentLevel
              );
            client.getDatabase().removeLink(user.uuid());
          } else {
            String newLevel = subscribers.get(boostyName);
            if (!newLevel.equalsIgnoreCase(currentLevel)) {
              client.debug(
                "player " +
                  user.playerName() +
                  " changed level: " +
                  currentLevel +
                  " -> " +
                  newLevel
              );
              takeRewards(user.playerName(), user.boostyName(), currentLevel);
              giveRewards(user.playerName(), user.boostyName(), newLevel);

              plugin
                .getDiscordBotManager()
                .updateUserRole(
                  user.uuid(),
                  user.playerName(),
                  currentLevel,
                  newLevel
                );

              client
                .getDiscordManager()
                .sendNotification(
                  "subscription",
                  user.playerName(),
                  user.boostyName(),
                  newLevel
                );
              client.getDatabase().updateLevel(user.uuid(), newLevel);
            }
          }
        }
      })
      .exceptionally(ex -> {
        client.debug("Error during sync task: " + ex.getMessage());
        return null;
      });
  }

  private void takeRewards(
    String playerName,
    String boostyName,
    String levelName
  ) {
    Bukkit.getScheduler().runTask(client.getPlugin(), () -> {
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

  private void giveRewards(
    String playerName,
    String boostyName,
    String levelName
  ) {
    Bukkit.getScheduler().runTask(client.getPlugin(), () -> {
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
}
