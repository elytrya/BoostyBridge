package onevnl.ru.elytrya.hooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import onevnl.ru.elytrya.api.BoostyClient;
import onevnl.ru.elytrya.models.BoostyUser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderProcessor extends PlaceholderExpansion {

  private static final long DEFAULT_CACHE_SECONDS = 30L;

  private final BoostyClient client;
  private final Map<UUID, CachedUser> userCache = new ConcurrentHashMap<>();
  private final Map<UUID, AtomicBoolean> refreshing = new ConcurrentHashMap<>();
  private final AtomicBoolean countRefreshing = new AtomicBoolean(false);

  private volatile int cachedCount = 0;
  private volatile long cachedCountAt = 0L;

  public PlaceholderProcessor(BoostyClient client) {
    this.client = client;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "boosty";
  }

  @Override
  public @NotNull String getAuthor() {
    return "Elytrya";
  }

  @Override
  public @NotNull String getVersion() {
    return "1.0.0";
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public String onRequest(OfflinePlayer player, @NotNull String params) {
    if (params.equalsIgnoreCase("global_subscribers")) {
      return String.valueOf(subscribersCount());
    }

    if (player == null) return "";

    BoostyUser user = cachedUser(player.getUniqueId());

    if (params.equalsIgnoreCase("level")) {
      return hasSubscription(user) ? user.levelName() : "None";
    }

    if (params.equalsIgnoreCase("name")) {
      return (user != null && user.boostyName() != null)
        ? user.boostyName()
        : "None";
    }

    if (params.equalsIgnoreCase("is_linked")) {
      return user != null ? "true" : "false";
    }

    if (params.equalsIgnoreCase("has_sub")) {
      return hasSubscription(user) ? "true" : "false";
    }

    return null;
  }

  private boolean hasSubscription(BoostyUser user) {
    return (
      user != null &&
      user.levelName() != null &&
      !user.levelName().equalsIgnoreCase("none")
    );
  }

  private long cacheMillis() {
    long seconds = client
      .getPlugin()
      .getConfig()
      .getLong("placeholders.cache_seconds", DEFAULT_CACHE_SECONDS);
    return Math.max(1L, seconds) * 1000L;
  }

  private BoostyUser cachedUser(UUID uuid) {
    CachedUser cached = userCache.get(uuid);
    long now = System.currentTimeMillis();

    if (cached == null || now - cached.storedAt > cacheMillis()) {
      refreshUser(uuid);
    }

    return cached != null ? cached.user : null;
  }

  private void refreshUser(UUID uuid) {
    AtomicBoolean flag = refreshing.computeIfAbsent(
      uuid,
      key -> new AtomicBoolean(false)
    );
    if (!flag.compareAndSet(false, true)) return;

    Bukkit.getScheduler()
      .runTaskAsynchronously(
        client.getPlugin(),
        () -> {
          try {
            BoostyUser user = client.getDatabase().getUser(uuid);
            userCache.put(
              uuid,
              new CachedUser(user, System.currentTimeMillis())
            );
            if (Bukkit.getPlayer(uuid) == null) {
              userCache.remove(uuid);
              refreshing.remove(uuid);
            }
          } finally {
            flag.set(false);
          }
        }
      );
  }

  private int subscribersCount() {
    long now = System.currentTimeMillis();

    if (now - cachedCountAt > cacheMillis() && countRefreshing.compareAndSet(false, true)) {
      Bukkit.getScheduler()
        .runTaskAsynchronously(
          client.getPlugin(),
          () -> {
            try {
              cachedCount = client.getDatabase().getActiveSubscribersCount();
              cachedCountAt = System.currentTimeMillis();
            } finally {
              countRefreshing.set(false);
            }
          }
        );
    }

    return cachedCount;
  }

  private static final class CachedUser {

    private final BoostyUser user;
    private final long storedAt;

    private CachedUser(BoostyUser user, long storedAt) {
      this.user = user;
      this.storedAt = storedAt;
    }
  }
}
