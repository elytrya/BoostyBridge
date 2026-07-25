package onevnl.ru.elytrya.commands.subcommands;

import java.security.SecureRandom;
import java.util.UUID;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.DiscordBotManager;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.models.BoostyUser;
import onevnl.ru.elytrya.models.PendingDiscordConfirm;
import onevnl.ru.elytrya.models.PendingDiscordLink;
import onevnl.ru.elytrya.util.DiscordNameValidator;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DiscordSubCommand implements SubCommand {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    BoostyBridge plugin = (BoostyBridge) client.getPlugin();
    DiscordBotManager bot = plugin.getDiscordBotManager();

    if (bot == null || !bot.isEnabled()) {
      player.sendMessage(
        msg.getMessage(
          "discord_bot_disabled",
          "&f\u041f\u0440\u0438\u0432\u044f\u0437\u043a\u0430 Discord \u0441\u0435\u0439\u0447\u0430\u0441 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430: Discord-\u0431\u043e\u0442 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435."
        )
      );
      return;
    }

    UUID uuid = player.getUniqueId();

    String action = args[1].toLowerCase();
    if (action.equals("confirm") || action.equals("reject")) {
      handleConfirmation(player, uuid, bot, action.equals("confirm"), msg);
      return;
    }

    Bukkit.getScheduler()
      .runTaskAsynchronously(
        client.getPlugin(),
        () -> startLinking(player, uuid, bot, args, msg)
      );
  }

  private void startLinking(
    Player player,
    UUID uuid,
    DiscordBotManager bot,
    String[] args,
    MessageManager msg
  ) {
    if (!player.isOnline()) return;

    String linked = client.getDatabase().getDiscordUser(uuid);
    if (linked != null && !linked.isEmpty()) {
      player.sendMessage(msg.getMessage("discord_already_linked"));
      return;
    }

    if (client.getPendingDiscordLinks().containsKey(uuid)) {
      player.sendMessage(
        msg.getMessage(
          "discord_code_pending",
          "&f\u041a\u043e\u0434 \u0443\u0436\u0435 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d \u0432 Discord. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0435\u0433\u043e \u0432 \u0447\u0430\u0442 \u0438\u043b\u0438 \u043f\u043e\u0434\u043e\u0436\u0434\u0438\u0442\u0435, \u043f\u043e\u043a\u0430 \u043e\u043d \u0438\u0441\u0442\u0435\u0447\u0451\u0442."
        )
      );
      return;
    }

    String discordTag = DiscordNameValidator.normalize(args[1]);

    if (!DiscordNameValidator.isValid(discordTag)) {
      player.sendMessage(
        msg.getMessage(
          "discord_invalid_name",
          "&f\u041d\u0435\u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b\u0439 \u044e\u0437\u0435\u0440\u043d\u0435\u0439\u043c Discord. \u0423\u043a\u0430\u0436\u0438\u0442\u0435 \u0438\u043c\u0435\u043d\u043d\u043e \u044e\u0437\u0435\u0440\u043d\u0435\u0439\u043c (\u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440 elytrya), \u0430 \u043d\u0435 \u043e\u0442\u043e\u0431\u0440\u0430\u0436\u0430\u0435\u043c\u043e\u0435 \u0438\u043c\u044f."
        )
      );
      return;
    }

    BoostyUser user = client.getDatabase().getUser(uuid);
    String currentLevel = (user != null) ? user.levelName() : "none";
    String boostyName = (user != null) ? user.boostyName() : null;

    player.sendMessage(
      msg.getMessage("discord_checking").replace("%discord_tag%", discordTag)
    );

    if (!bot.isVerificationEnabled()) {
      assignRole(player, bot, discordTag, boostyName, currentLevel, msg);
      return;
    }

    String code = generateCode();

    bot
      .requestVerification(discordTag, player.getName(), code)
      .thenAccept(request -> {
        switch (request.status()) {
          case SENT -> {
            client
              .getPendingDiscordLinks()
              .put(
                uuid,
                new PendingDiscordLink(discordTag, request.memberId(), code)
              );
            player.sendMessage(
              msg
                .getMessage(
                  "discord_code_sent",
                  "&f\u041a\u043e\u0434 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u044f \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d \u0432 \u041b\u0421 Discord \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044e &#FFB6C1%discord_tag%&f. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0435\u0433\u043e \u0432 \u0447\u0430\u0442."
                )
                .replace("%discord_tag%", discordTag)
            );
            player.sendMessage(
              msg
                .getMessage(
                  "discord_code_ttl",
                  "&f\u041a\u043e\u0434 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0442\u0435\u043b\u0435\u043d &#FFB6C1%minutes% \u043c\u0438\u043d&f. \u041f\u043e\u043f\u044b\u0442\u043e\u043a \u0432\u0432\u043e\u0434\u0430: &#FFB6C1%attempts%"
                )
                .replace(
                  "%minutes%",
                  String.valueOf(PendingDiscordLink.TTL_MILLIS / 60000L)
                )
                .replace(
                  "%attempts%",
                  String.valueOf(PendingDiscordLink.MAX_ATTEMPTS)
                )
            );
            client.debug(
              "Discord verification code sent to member " + request.memberId()
            );
          }
          case MEMBER_NOT_FOUND -> player.sendMessage(
            msg
              .getMessage("discord_not_found")
              .replace("%discord_tag%", discordTag)
          );
          case DM_FAILED -> player.sendMessage(
            msg
              .getMessage(
                "discord_dm_failed",
                "&f\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u043a\u043e\u0434 \u0432 \u041b\u0421 &#FFB6C1%discord_tag%&f. \u0420\u0430\u0437\u0440\u0435\u0448\u0438\u0442\u0435 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u044f \u043e\u0442 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432 \u0441\u0435\u0440\u0432\u0435\u0440\u0430 \u0438 \u043f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0441\u043d\u043e\u0432\u0430."
              )
              .replace("%discord_tag%", discordTag)
          );
          default -> player.sendMessage(
            msg.getMessage(
              "discord_bot_disabled",
              "&f\u041f\u0440\u0438\u0432\u044f\u0437\u043a\u0430 Discord \u0441\u0435\u0439\u0447\u0430\u0441 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430: Discord-\u0431\u043e\u0442 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435."
            )
          );
        }
      })
      .exceptionally(ex -> {
        player.sendMessage(msg.getMessage("discord_error"));
        client
          .getPlugin()
          .getLogger()
          .severe(
            "\u041e\u0448\u0438\u0431\u043a\u0430 \u0432 /boosty discord: " +
            ex.getClass().getSimpleName()
          );
        return null;
      });
  }

  private void assignRole(
    Player player,
    DiscordBotManager bot,
    String discordTag,
    String boostyName,
    String currentLevel,
    MessageManager msg
  ) {
    bot
      .updateUserRole(
        player.getUniqueId(),
        player.getName(),
        discordTag,
        currentLevel
      )
      .thenAccept(success -> {
        if (success) {
          client.getDatabase().setDiscordUser(player.getUniqueId(), discordTag);
          if (boostyName != null && !boostyName.isEmpty()) {
            client
              .getDatabase()
              .saveLink(
                player.getUniqueId(),
                player.getName(),
                boostyName,
                currentLevel
              );
          }
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
        return null;
      });
  }

  private void handleConfirmation(
    Player player,
    UUID uuid,
    DiscordBotManager bot,
    boolean confirmed,
    MessageManager msg
  ) {
    PendingDiscordConfirm pending = client.getPendingDiscordConfirms().get(uuid);

    if (pending == null || pending.isExpired()) {
      client.getPendingDiscordConfirms().remove(uuid);
      player.sendMessage(
        msg.getMessage(
          "discord_confirm_nothing",
          "&fНет привязки Discord, ожидающей подтверждения."
        )
      );
      return;
    }

    String discordTag = pending.discordName();

    if (confirmed) {
      bot
        .confirmDiscordLink(uuid, player.getName())
        .thenAccept(done -> {
          player.sendMessage(
            msg
              .getMessage(
                "discord_confirm_done",
                "&f\u041e\u0442\u043b\u0438\u0447\u043d\u043e, \u0430\u043a\u043a\u0430\u0443\u043d\u0442 &#FFB6C1%discord_tag%&f \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d \u0437\u0430 \u0432\u0430\u043c\u0438, \u0440\u043e\u043b\u044c \u0432\u044b\u0434\u0430\u043d\u0430."
              )
              .replace("%discord_tag%", discordTag)
          );
        })
        .exceptionally(ex -> {
          player.sendMessage(msg.getMessage("discord_error"));
          return null;
        });
      client.debug("Discord link confirmed by " + player.getName());
      return;
    }

    bot
      .rejectDiscordLink(uuid, player.getName())
      .thenAccept(done -> {
        player.sendMessage(
          msg
            .getMessage(
              "discord_confirm_cancelled",
              "&fПривязка к &#FFB6C1%discord_tag%&f отменена, роль снята."
            )
            .replace("%discord_tag%", discordTag)
        );
        bot.sendChangePrompt(uuid);
      })
      .exceptionally(ex -> {
        player.sendMessage(msg.getMessage("discord_error"));
        return null;
      });
    client.debug("Discord link rejected by " + player.getName());
  }

  private String generateCode() {
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }
}
