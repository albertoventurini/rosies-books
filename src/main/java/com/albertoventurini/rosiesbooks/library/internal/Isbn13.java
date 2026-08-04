package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;

/** A validated, separator-free ISBN-13. */
public record Isbn13(String value) {

  public Isbn13 {
    Objects.requireNonNull(value, "value");
    if (value.length() != 13
        || !(value.startsWith("978") || value.startsWith("979"))
        || !hasOnlyAsciiDigits(value)
        || value.charAt(12) != checkDigit(value.substring(0, 12))) {
      throw new IllegalArgumentException("Invalid ISBN-13");
    }
  }

  public static Isbn13 parse(String input) {
    Objects.requireNonNull(input, "input");
    StringBuilder normalized = new StringBuilder(input.length());
    for (int index = 0; index < input.length(); index++) {
      char character = input.charAt(index);
      if (character == '-' || Character.isWhitespace(character)) {
        continue;
      }
      normalized.append(character);
    }
    return new Isbn13(normalized.toString());
  }

  static char checkDigit(String twelveDigits) {
    int sum = 0;
    for (int index = 0; index < twelveDigits.length(); index++) {
      sum += (twelveDigits.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
    }
    return (char) ('0' + (10 - sum % 10) % 10);
  }

  private static boolean hasOnlyAsciiDigits(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < '0' || character > '9') {
        return false;
      }
    }
    return true;
  }
}
