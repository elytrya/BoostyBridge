package onevnl.ru.elytrya.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;

public final class ClickableMessages {

  private ClickableMessages() {}

  public static BaseComponent text(String legacyText) {
    return new TextComponent(TextComponent.fromLegacyText(legacyText));
  }

  public static BaseComponent runButton(
    String label,
    String hover,
    String command
  ) {
    return button(label, hover, command, ClickEvent.Action.RUN_COMMAND);
  }

  public static BaseComponent suggestButton(
    String label,
    String hover,
    String command
  ) {
    return button(label, hover, command, ClickEvent.Action.SUGGEST_COMMAND);
  }

  private static BaseComponent button(
    String label,
    String hover,
    String command,
    ClickEvent.Action action
  ) {
    TextComponent component = new TextComponent(
      TextComponent.fromLegacyText(label)
    );
    if (hover != null && !hover.isEmpty()) {
      component.setHoverEvent(
        new HoverEvent(
          HoverEvent.Action.SHOW_TEXT,
          new Text(TextComponent.fromLegacyText(hover))
        )
      );
    }
    component.setClickEvent(new ClickEvent(action, command));
    return component;
  }

  public static void send(Player player, BaseComponent... parts) {
    if (player == null || parts == null || parts.length == 0) return;

    TextComponent line = new TextComponent();
    for (BaseComponent part : parts) {
      if (part != null) line.addExtra(part);
    }
    player.spigot().sendMessage(line);
  }
}
