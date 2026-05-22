package onevnl.ru.elytrya;

import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.DiscordBotManager;
import onevnl.ru.elytrya.commands.BoostyCommand;
import onevnl.ru.elytrya.hooks.PlaceholderProcessor;
import onevnl.ru.elytrya.listeners.ChatListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BoostyBridge extends JavaPlugin {

  private BoostyClient boostyClient;
  private DiscordBotManager discordBotManager;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    this.boostyClient = new BoostyClient(this);
    this.discordBotManager = new DiscordBotManager(this.boostyClient);

    getCommand("boosty").setExecutor(new BoostyCommand(this.boostyClient));
    getServer()
      .getPluginManager()
      .registerEvents(new ChatListener(this.boostyClient), this);

    if (getConfig().getBoolean("bstats", true)) {
      int pluginId = 30011;
      new Metrics(this, pluginId);
      getLogger().info("bStats metrics enabled.");
    }

    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      new PlaceholderProcessor(boostyClient).register();
      getLogger().info("PlaceholderAPI expansion registered successfully.");
    }

    getLogger().info("Checking Boosty authorization...");

    boostyClient
      .getAuthManager()
      .checkAndRefreshToken()
      .thenCompose(v -> boostyClient.getBlogManager().getBlogStats())
      .whenComplete((stats, ex) -> {
        if (ex != null) {
          getLogger().severe(
            "====================================================="
          );
          getLogger().severe(
            "[RU] ОШИБКА SSL: Скорее всего, ваш токен (auth_data) невалидный или устарел!"
          );
          getLogger().severe(
            "[RU] Сервер Boosty разрывает соединение из-за неправильных данных авторизации."
          );
          getLogger().severe(
            "[RU] Пожалуйста, получите новые данные в браузере и обновите config.yml"
          );
          getLogger().severe(
            "-----------------------------------------------------"
          );
          getLogger().severe(
            "[EN] SSL ERROR: Most likely, your token (auth_data) is invalid or expired!"
          );
          getLogger().severe(
            "[EN] Boosty server terminates the connection due to incorrect auth data."
          );
          getLogger().severe(
            "[EN] Please retrieve new data from your browser and update config.yml"
          );
          getLogger().severe(
            "====================================================="
          );
        } else if (stats != null) {
          getLogger().info("Successfully connected to Boosty! Token is valid.");
        } else {
          getLogger().severe(
            "Failed to connect to Boosty. Token is invalid or missing in config.yml!"
          );
        }
      });
  }

  @Override
  public void onDisable() {
    if (this.discordBotManager != null) {
      this.discordBotManager.shutdown();
    }
    if (this.boostyClient != null) {
      this.boostyClient.disable();
    }
  }

  public BoostyClient getBoostyClient() {
    return boostyClient;
  }

  public DiscordBotManager getDiscordBotManager() {
    return discordBotManager;
  }
}
