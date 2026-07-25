package onevnl.ru.elytrya.util;

import java.util.regex.Pattern;

public final class BoostyNameValidator {

  public static final int MIN_LENGTH = 1;
  public static final int MAX_LENGTH = 128;

  private static final Pattern DISALLOWED = Pattern.compile(
    "[\\p{Cntrl}\\p{Cc}\\p{Cf}\u00a7&%;|`$<>\\\\\"\\n\\r\\t]"
  );
  private static final Pattern SPACES = Pattern.compile("\\s+");

  private BoostyNameValidator() {}

  public static String normalize(String boostyName) {
    if (boostyName == null) return "";
    return SPACES.matcher(boostyName.trim()).replaceAll(" ");
  }

  public static boolean isValid(String boostyName) {
    if (boostyName == null) return false;
    String value = normalize(boostyName);
    if (value.isEmpty()) return false;
    if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
      return false;
    }
    return !DISALLOWED.matcher(value).find();
  }

  public static String sanitize(String boostyName) {
    if (boostyName == null) return "";
    String value = DISALLOWED.matcher(boostyName).replaceAll("");
    value = normalize(value);
    if (value.length() > MAX_LENGTH) {
      value = value.substring(0, MAX_LENGTH).trim();
    }
    return value;
  }
}
