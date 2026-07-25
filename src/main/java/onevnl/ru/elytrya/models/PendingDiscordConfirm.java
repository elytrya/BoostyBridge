package onevnl.ru.elytrya.models;

public record PendingDiscordConfirm(
  String discordName,
  String levelName,
  long createdAt
) {
  public static final long TTL_MILLIS = 1800000L;

  public PendingDiscordConfirm(String discordName, String levelName) {
    this(discordName, levelName, System.currentTimeMillis());
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - createdAt > TTL_MILLIS;
  }
}
