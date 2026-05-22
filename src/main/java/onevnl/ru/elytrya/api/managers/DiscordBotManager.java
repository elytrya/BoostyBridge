package onevnl.ru.elytrya.api.managers;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import onevnl.ru.elytrya.api.BoostyClient;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class DiscordBotManager {

  private final BoostyClient client;
  private JDA jda;
  private boolean enabled;
  private long guildId;
  private long channelId;

  public DiscordBotManager(BoostyClient client) {
    this.client = client;
    reload();
  }

  public void reload() {
    FileConfiguration config = client.getPlugin().getConfig();
    this.enabled = config.getBoolean("discord.bot.enabled", false);
    if (!enabled) {
      stop();
      return;
    }

    this.guildId = config.getLong("discord.bot.guild_id", 0);
    this.channelId = config.getLong("discord.bot.channel_id", 0);
    String token = config.getString("discord.bot.token", "");

    if (
      token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")
    ) {
      this.enabled = false;
      return;
    }

    if (jda == null) {
      try {
        this.jda = JDABuilder.createDefault(token)
          .enableIntents(GatewayIntent.GUILD_MEMBERS)
          .build()
          .awaitReady();
      } catch (Exception e) {
        client
          .getPlugin()
          .getLogger()
          .severe("Failed to start Discord bot: " + e.getMessage());
        this.enabled = false;
      }
    }
  }

  public void shutdown() {
    stop();
  }

  public void stop() {
    if (jda != null) {
      jda.shutdown();
      jda = null;
    }
  }

  public CompletableFuture<Boolean> updateUserRole(
    UUID uuid,
    String playerName,
    String discordName,
    String boostyLevel
  ) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    if (!enabled || jda == null) {
      future.complete(false);
      return future;
    }

    Guild guild = jda.getGuildById(guildId);
    if (guild == null) {
      future.complete(false);
      return future;
    }

    long roleId = getRoleIdForLevel(boostyLevel);
    if (roleId == 0) {
      future.complete(true);
      return future;
    }

    findMember(guild, discordName).thenAccept(member -> {
      if (member == null) {
        bidirectionalCheck(guild, playerName, roleId, boostyLevel, future);
      } else {
        proceedWithRole(
          guild,
          member,
          roleId,
          playerName,
          boostyLevel,
          future,
          true
        );
      }
    });
    return future;
  }

  private void bidirectionalCheck(
    Guild guild,
    String playerName,
    long roleId,
    String boostyLevel,
    CompletableFuture<Boolean> future
  ) {
    findMember(guild, playerName).thenAccept(member -> {
      if (member != null) {
        proceedWithRole(
          guild,
          member,
          roleId,
          playerName,
          boostyLevel,
          future,
          true
        );
      } else {
        future.complete(false);
      }
    });
  }

  public CompletableFuture<Boolean> removeUserRole(
    UUID uuid,
    String playerName,
    String boostyLevel
  ) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    if (!enabled || jda == null) {
      future.complete(false);
      return future;
    }

    Guild guild = jda.getGuildById(guildId);
    if (guild == null) {
      future.complete(false);
      return future;
    }

    long roleId = getRoleIdForLevel(boostyLevel);
    if (roleId == 0) {
      future.complete(true);
      return future;
    }

    findMember(guild, playerName).thenAccept(member -> {
      if (member == null) {
        future.complete(false);
      } else {
        proceedWithRole(
          guild,
          member,
          roleId,
          playerName,
          boostyLevel,
          future,
          false
        );
      }
    });
    return future;
  }

  private void proceedWithRole(
    Guild guild,
    Member member,
    long roleId,
    String playerName,
    String boostyLevel,
    CompletableFuture<Boolean> future,
    boolean add
  ) {
    Role targetRole = guild.getRoleById(roleId);
    if (targetRole == null) {
      future.complete(false);
      return;
    }

    if (add) {
      guild.addRoleToMember(member, targetRole).queue(
        s -> {
          logEmbedToChannel(
            guild,
            "discord.role-given-embed",
            playerName,
            targetRole.getName(),
            boostyLevel,
            member.getAsMention()
          );
          future.complete(true);
        },
        f -> future.complete(false)
      );
    } else {
      guild.removeRoleFromMember(member, targetRole).queue(
        s -> {
          logEmbedToChannel(
            guild,
            "discord.role-removed-embed",
            playerName,
            targetRole.getName(),
            boostyLevel,
            member.getAsMention()
          );
          future.complete(true);
        },
        f -> future.complete(false)
      );
    }
  }

  private void logEmbedToChannel(
    Guild guild,
    String configPath,
    String playerName,
    String roleName,
    String boostyLevel,
    String mention
  ) {
    if (channelId == 0) return;
    try {
      TextChannel channel = guild.getTextChannelById(channelId);
      if (channel == null) return;

      File file = new File(client.getPlugin().getDataFolder(), "messages.yml");
      FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(
        file
      );
      ConfigurationSection section = messagesConfig.getConfigurationSection(
        configPath
      );

      if (section == null) return;

      EmbedBuilder embed = new EmbedBuilder();

      String colorStr = section.getString("color", "#FFB6C1");
      try {
        embed.setColor(Color.decode(colorStr));
      } catch (Exception e) {
        embed.setColor(Color.PINK);
      }

      String title = section.getString("title", "Лог подписки");
      embed.setTitle(
        title
          .replace("{player}", playerName)
          .replace("{role_name}", roleName)
          .replace("{level_name}", boostyLevel)
      );

      String description = section.getString("description", "");
      if (!description.isEmpty()) {
        embed.setDescription(
          description
            .replace("{player}", playerName)
            .replace("{role_name}", roleName)
            .replace("{level_name}", boostyLevel)
            .replace("{ping}", mention)
        );
      }

      if (section.contains("fields")) {
        for (java.util.Map<?, ?> map : section.getMapList("fields")) {
          String fName = String.valueOf(map.get("name"));
          String fValue = String.valueOf(map.get("value"));
          boolean inline =
            map.containsKey("inline") && (boolean) map.get("inline");

          embed.addField(
            fName
              .replace("{player}", playerName)
              .replace("{role_name}", roleName)
              .replace("{level_name}", boostyLevel),
            fValue
              .replace("{player}", playerName)
              .replace("{role_name}", roleName)
              .replace("{level_name}", boostyLevel)
              .replace("{ping}", mention),
            inline
          );
        }
      }

      String footer = section.getString("footer", "");
      if (!footer.isEmpty()) {
        embed.setFooter(
          footer
            .replace("{player}", playerName)
            .replace("{role_name}", roleName)
            .replace("{level_name}", boostyLevel)
        );
      }

      channel.sendMessageEmbeds(embed.build()).queue();
    } catch (Exception e) {
      client
        .getPlugin()
        .getLogger()
        .warning(
          "[Discord Logs] Ошибка сборки Embed сообщения: " + e.getMessage()
        );
    }
  }

  private long getRoleIdForLevel(String level) {
    FileConfiguration config = client.getPlugin().getConfig();
    String basePath = "discord.bot.roles." + level;
    if (!config.contains(basePath)) return 0;
    if (config.isConfigurationSection(basePath)) {
      ConfigurationSection section = config.getConfigurationSection(basePath);
      return section != null ? section.getLong("role_id", 0) : 0;
    }
    return config.getLong(basePath, 0);
  }

  private CompletableFuture<Member> findMember(Guild guild, String name) {
    CompletableFuture<Member> future = new CompletableFuture<>();
    List<Member> cached = guild.getMembersByName(name, true);
    if (!cached.isEmpty()) {
      future.complete(cached.get(0));
      return future;
    }

    guild
      .retrieveMembersByPrefix(name, 5)
      .onSuccess(members -> {
        if (members != null && !members.isEmpty()) {
          future.complete(members.get(0));
        } else {
          future.complete(null);
        }
      })
      .onError(e -> future.complete(null));
    return future;
  }
}
