package onevnl.ru.elytrya.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import onevnl.ru.elytrya.BoostyBridge;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.commands.subcommands.*;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BoostyCommand implements CommandExecutor, TabCompleter {

  private final BoostyClient client;
  private final Map<String, SubCommand> subCommands = new HashMap<>();

  public BoostyCommand(BoostyClient client) {
    this.client = client;
    registerSubCommand(new ReloadSubCommand(client));
    registerSubCommand(new LinkSubCommand(client));
    registerSubCommand(new InfoSubCommand(client));
    registerSubCommand(new AdminSubCommand(client));
    registerSubCommand(new DiscordSubCommand(client));

    Bukkit.getLogger().info(
      "[BoostyBridge] Command initialized. Discord enabled status: " +
        isDiscordEnabled()
    );
  }

  private void registerSubCommand(SubCommand cmd) {
    subCommands.put(cmd.getName().toLowerCase(), cmd);
  }

  private boolean isDiscordEnabled() {
    BoostyBridge plugin = (BoostyBridge) Bukkit.getPluginManager().getPlugin(
      "BoostyBridge"
    );
    if (plugin != null) {
      return plugin.getConfig().getBoolean("discord.bot.enabled", false);
    }
    return client
      .getPlugin()
      .getConfig()
      .getBoolean("discord.bot.enabled", false);
  }

  @Override
  public boolean onCommand(
    CommandSender sender,
    Command command,
    String label,
    String[] args
  ) {
    MessageManager msg = client.getMessageManager();
    boolean discordEnabled = isDiscordEnabled();

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      List<String> helpLines = msg.getMessageList("help_menu");
      for (String line : helpLines) {
        if (
          !discordEnabled &&
          (line.contains("/boosty discord") || line.contains("discord"))
        ) {
          continue;
        }
        sender.sendMessage(line);
      }

      if (
        sender.hasPermission("boosty.admin.unlink") ||
        sender.hasPermission("boosty.admin.info") ||
        sender.hasPermission("boosty.admin.forcelink") ||
        sender.hasPermission("boosty.admin.forcesync")
      ) {
        List<String> adminHelp = msg.getMessageList("help_menu_admin");
        for (String line : adminHelp) {
          sender.sendMessage(line);
        }
      }
      return true;
    }

    String subName = args[0].toLowerCase();

    if (subName.equals("discord") && !discordEnabled) {
      sender.sendMessage(msg.getMessage("unknown_command"));
      return true;
    }

    SubCommand sub = subCommands.get(subName);

    if (sub == null) {
      sender.sendMessage(msg.getMessage("unknown_command"));
      return true;
    }

    if (
      sub.getPermission() != null && !sender.hasPermission(sub.getPermission())
    ) {
      sender.sendMessage(msg.getMessage("no_permission"));
      return true;
    }

    sub.execute(sender, args);
    return true;
  }

  @Override
  public List<String> onTabComplete(
    CommandSender sender,
    Command command,
    String alias,
    String[] args
  ) {
    List<String> completions = new ArrayList<>();
    boolean discordEnabled = isDiscordEnabled();

    if (args.length == 1) {
      String input = args[0].toLowerCase();
      subCommands
        .values()
        .stream()
        .filter(sub -> {
          if (sub.getName().equalsIgnoreCase("discord") && !discordEnabled) {
            return false;
          }
          return (
            sub.getPermission() == null ||
            sender.hasPermission(sub.getPermission())
          );
        })
        .map(SubCommand::getName)
        .filter(name -> name.startsWith(input))
        .forEach(completions::add);

      if ("help".startsWith(input)) {
        completions.add("help");
      }
    } else if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
      handleAdminTab(sender, completions, args, discordEnabled);
    } else if (
      args.length == 2 && args[0].equalsIgnoreCase("discord") && discordEnabled
    ) {
      if (sender instanceof Player) completions.add("<discord_username>");
    }
    return completions;
  }

  private void handleAdminTab(
    CommandSender sender,
    List<String> list,
    String[] args,
    boolean discordEnabled
  ) {
    if (args.length == 2) {
      String input = args[1].toLowerCase();

      List<String> adminActions = new ArrayList<>();
      adminActions.add("unlink");
      adminActions.add("info");
      adminActions.add("forcelink");
      adminActions.add("forcesync");
      if (discordEnabled) {
        adminActions.add("setdiscord");
      }

      for (String s : adminActions) {
        if (s.startsWith(input) && sender.hasPermission("boosty.admin." + s)) {
          list.add(s);
        }
      }
    } else if (args.length == 3) {
      handlePlayerTab(list, args, discordEnabled);
    }
  }

  private void handlePlayerTab(
    List<String> list,
    String[] args,
    boolean discordEnabled
  ) {
    String sub = args[1].toLowerCase();
    String input = args[2].toLowerCase();

    if (
      sub.equals("unlink") || sub.equals("info") || sub.equals("setdiscord")
    ) {
      if (sub.equals("setdiscord") && !discordEnabled) return;

      client
        .getDatabase()
        .getAllUsers()
        .stream()
        .map(BoostyUser::playerName)
        .filter(n -> n.toLowerCase().startsWith(input))
        .forEach(list::add);
    } else if (sub.equals("forcelink")) {
      client
        .getPlugin()
        .getServer()
        .getOnlinePlayers()
        .stream()
        .map(Player::getName)
        .filter(n -> n.toLowerCase().startsWith(input))
        .forEach(list::add);
    }
  }
}
