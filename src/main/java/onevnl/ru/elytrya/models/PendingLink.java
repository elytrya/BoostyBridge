package onevnl.ru.elytrya.models;

import java.util.concurrent.atomic.AtomicInteger;

public record PendingLink(
  String boostyName,
  String levelName,
  String verificationValue,
  String verificationType,
  long createdAt,
  AtomicInteger attempts
) {
  public static final long TTL_MILLIS = 300000L;
  public static final int MAX_ATTEMPTS = 3;

  public PendingLink(
    String boostyName,
    String levelName,
    String verificationValue,
    String verificationType
  ) {
    this(
      boostyName,
      levelName,
      verificationValue,
      verificationType,
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
