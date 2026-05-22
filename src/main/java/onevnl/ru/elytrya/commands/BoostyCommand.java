package onevnl.ru.elytrya.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.api.managers.MessageManager;
import onevnl.ru.elytrya.commands.subcommands.*;
import onevnl.ru.elytrya.models.BoostyUser;
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
  }

  private void registerSubCommand(SubCommand cmd) {
    subCommands.put(cmd.getName().toLowerCase(), cmd);
  }

  @Override
  public boolean onCommand(
    CommandSender sender,
    Command command,
    String label,
    String[] args
  ) {
    MessageManager msg = client.getMessageManager();

    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }

    String subName = args[0].toLowerCase();
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

  private void sendHelp(CommandSender sender) {
    MessageManager msg = client.getMessageManager();

    List<String> lines = msg.getMessageList("help_menu");
    if (lines != null) {
      for (String line : lines) {
        sender.sendMessage(line);
      }
    }

    if (sender.hasPermission("boosty.admin.*")) {
      List<String> adminLines = msg.getMessageList("help_menu_admin");
      if (adminLines != null) {
        for (String line : adminLines) {
          sender.sendMessage(line);
        }
      }
    }
  }

  @Override
  public List<String> onTabComplete(
    CommandSender sender,
    Command command,
    String alias,
    String[] args
  ) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      String input = args[0].toLowerCase();
      subCommands
        .values()
        .stream()
        .filter(
          sub ->
            sub.getPermission() == null ||
            sender.hasPermission(sub.getPermission())
        )
        .map(SubCommand::getName)
        .filter(name -> name.startsWith(input))
        .forEach(completions::add);
    } else if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
      handleAdminTab(sender, completions, args);
    } else if (args.length == 2 && args[0].equalsIgnoreCase("discord")) {
      if (sender instanceof Player) completions.add("<discord_username>");
    }
    return completions;
  }

  private void handleAdminTab(
    CommandSender sender,
    List<String> list,
    String[] args
  ) {
    if (args.length == 2) {
      String input = args[1].toLowerCase();
      String[] adminActions = {
        "unlink",
        "info",
        "forcelink",
        "forcesync",
        "setdiscord",
      };
      for (String s : adminActions) {
        if (s.startsWith(input) && sender.hasPermission("boosty.admin." + s)) {
          list.add(s);
        }
      }
    } else if (args.length == 3) {
      handlePlayerTab(list, args);
    }
  }

  private void handlePlayerTab(List<String> list, String[] args) {
    String sub = args[1].toLowerCase();
    String input = args[2].toLowerCase();
    if (
      sub.equals("unlink") || sub.equals("info") || sub.equals("setdiscord")
    ) {
      client
        .getDatabase()
        .getAllUsers()
        .stream()
        .map(BoostyUser::playerName)
        .filter(n -> n.toLowerCase().startsWith(input))
        .forEach(list::add);
    }
  }
}
