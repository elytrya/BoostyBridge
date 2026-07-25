package onevnl.ru.elytrya.util;

import java.util.regex.Pattern;

public final class DiscordNameValidator {

  public static final int MIN_LENGTH = 2;
  public static final int MAX_LENGTH = 32;

  private static final Pattern ALLOWED = Pattern.compile(
    "^[A-Za-z0-9._]{2,32}(#\\d{4})?$"
  );

  private DiscordNameValidator() {}

  public static String normalize(String input) {
    if (input == null) return "";
    String trimmed = input.trim();
    if (trimmed.startsWith("@")) {
      trimmed = trimmed.substring(1);
    }
    return trimmed;
  }

  public static boolean isValid(String input) {
    String normalized = normalize(input);
    if (normalized.isEmpty()) return false;
    return ALLOWED.matcher(normalized).matches();
  }
}
