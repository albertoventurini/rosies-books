package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Locale;
import java.util.Objects;

/** A validated, separator-free ISBN-10. */
public record Isbn10(String value) {

  public Isbn10 {
    Objects.requireNonNull(value, "value");
    if (value.length() != 10 || !hasValidCharacters(value) || !hasValidChecksum(value)) {
      throw new IllegalArgumentException("Invalid ISBN-10");
    }
  }

  public static Isbn10 parse(String input) {
    return new Isbn10(normalize(input).toUpperCase(Locale.ROOT));
  }

  public Isbn13 toIsbn13() {
    String body = "978" + value.substring(0, 9);
    return new Isbn13(body + Isbn13.checkDigit(body));
  }

  private static boolean hasValidCharacters(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character >= '0' && character <= '9') {
        continue;
      }
      if (index == 9 && character == 'X') {
        continue;
      }
      return false;
    }
    return true;
  }

  private static boolean hasValidChecksum(String value) {
    int sum = 0;
    for (int index = 0; index < value.length(); index++) {
      int digit = value.charAt(index) == 'X' ? 10 : value.charAt(index) - '0';
      sum += (10 - index) * digit;
    }
    return sum % 11 == 0;
  }

  private static String normalize(String input) {
    Objects.requireNonNull(input, "input");
    StringBuilder normalized = new StringBuilder(input.length());
    for (int index = 0; index < input.length(); index++) {
      char character = input.charAt(index);
      if (character == '-' || Character.isWhitespace(character)) {
        continue;
      }
      normalized.append(character);
    }
    return normalized.toString();
  }
}
