package onevnl.ru.elytrya.tasks;

import java.util.Map;
import java.util.UUID;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.models.PendingDiscordConfirm;
import onevnl.ru.elytrya.models.PendingDiscordLink;
import onevnl.ru.elytrya.models.PendingLink;
import org.bukkit.scheduler.BukkitRunnable;

public class PendingLinkCleanupTask extends BukkitRunnable {

  private final BoostyClient client;

  public PendingLinkCleanupTask(BoostyClient client) {
    this.client = client;
  }

  @Override
  public void run() {
    cleanupBoostyLinks();
    cleanupDiscordLinks();
    cleanupDiscordConfirms();
  }

  private void cleanupDiscordConfirms() {
    Map<UUID, PendingDiscordConfirm> pending =
      client.getPendingDiscordConfirms();
    if (pending.isEmpty()) return;

    int before = pending.size();
    pending.entrySet().removeIf(entry -> entry.getValue().isExpired());
    int removed = before - pending.size();

    if (removed > 0) {
      client.debug(
        "Removed " + removed + " expired Discord confirmation request(s)."
      );
    }
  }

  private void cleanupBoostyLinks() {
    Map<UUID, PendingLink> pending = client.getPendingLinks();
    if (pending.isEmpty()) return;

    int before = pending.size();
    pending.entrySet().removeIf(entry -> entry.getValue().isExpired());
    int removed = before - pending.size();

    if (removed > 0) {
      client.debug("Removed " + removed + " expired pending link(s).");
    }
  }

  private void cleanupDiscordLinks() {
    Map<UUID, PendingDiscordLink> pending = client.getPendingDiscordLinks();
    if (pending.isEmpty()) return;

    int before = pending.size();
    pending.entrySet().removeIf(entry -> entry.getValue().isExpired());
    int removed = before - pending.size();

    if (removed > 0) {
      client.debug(
        "Removed " + removed + " expired pending Discord verification(s)."
      );
    }
  }
}
