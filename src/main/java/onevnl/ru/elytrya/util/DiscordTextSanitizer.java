package onevnl.ru.elytrya.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiscordTextSanitizer {

  public static final int TITLE_LIMIT = 256;
  public static final int DESCRIPTION_LIMIT = 4096;
  public static final int FIELD_NAME_LIMIT = 256;
  public static final int FIELD_VALUE_LIMIT = 1024;
  public static final int FOOTER_LIMIT = 2048;
  public static final int VALUE_LIMIT = 200;

  private static final Pattern CONTROL_CHARS = Pattern.compile(
    "[\\p{Cntrl}\\p{Cf}]"
  );
  private static final Pattern MENTION = Pattern.compile("^<@!?\\d{1,32}>$");
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final Pattern MARKDOWN_LINK = Pattern.compile(
    "\\[([^\\]]*)\\]\\(\\s*<?([^>\\s)]+)>?\\s*\\)"
  );
  private static final Pattern URL_SCHEME = Pattern.compile(
    "(?i)^(https?|discord)://"
  );

  private DiscordTextSanitizer() {}

  public static String value(String input) {
    return value(input, VALUE_LIMIT);
  }

  public static String value(String input, int limit) {
    if (input == null) return "";
    String cleaned = CONTROL_CHARS.matcher(input).replaceAll(" ");
    cleaned = SPACES.matcher(cleaned).replaceAll(" ").trim();
    cleaned = cleaned
      .replace("@everyone", "@\u200beveryone")
      .replace("@here", "@\u200bhere")
      .replace("\\", "\u2216")
      .replace("`", "\u02cb")
      .replace("<@", "<\u200b@")
      .replace("<#", "<\u200b#");
    return truncate(cleaned, limit);
  }

  public static String footer(String input) {
    if (input == null) return "";
    Matcher matcher = MARKDOWN_LINK.matcher(input);
    StringBuilder builder = new StringBuilder();
    while (matcher.find()) {
      String label = matcher.group(1).trim();
      String url = URL_SCHEME.matcher(matcher.group(2).trim()).replaceFirst("");
      String replacement;
      if (url.isEmpty()) {
        replacement = label;
      } else if (label.isEmpty() || label.equalsIgnoreCase(url)) {
        replacement = url;
      } else {
        replacement = label + " - " + url;
      }
      matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(builder);

    String cleaned = CONTROL_CHARS.matcher(builder.toString()).replaceAll(" ");
    cleaned = cleaned
      .replace("***", "")
      .replace("**", "")
      .replace("__", "")
      .replace("~~", "")
      .replace("||", "")
      .replace("`", "")
      .replace("@everyone", "@\u200beveryone")
      .replace("@here", "@\u200bhere");
    cleaned = SPACES.matcher(cleaned).replaceAll(" ").trim();
    return truncate(cleaned, FOOTER_LIMIT);
  }

  public static String mention(String input) {
    if (input == null) return "";
    String trimmed = input.trim();
    return MENTION.matcher(trimmed).matches() ? trimmed : "";
  }

  public static String truncate(String input, int limit) {
    if (input == null) return "";
    if (limit <= 0 || input.length() <= limit) return input;
    return input.substring(0, limit);
  }
}
