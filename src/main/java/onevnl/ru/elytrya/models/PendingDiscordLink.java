package onevnl.ru.elytrya.models;

import java.util.concurrent.atomic.AtomicInteger;

public record PendingDiscordLink(
  String discordName,
  String memberId,
  String code,
  long createdAt,
  AtomicInteger attempts
) {
  public static final long TTL_MILLIS = 300000L;
  public static final int MAX_ATTEMPTS = 3;

  public PendingDiscordLink(String discordName, String memberId, String code) {
    this(
      discordName,
      memberId,
      code,
      System.currentTimeMillis(),
      new AtomicInteger(0)
    );
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - createdAt > TTL_MILLIS;
  }

  public int registerFailedAttempt() {
    return attempts.incrementAndGet();
  }

  public int attemptsLeft() {
    return Math.max(0, MAX_ATTEMPTS - attempts.get());
  }
}
