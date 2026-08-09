package com.albertoventurini.rosiesbooks.provider.api;

import java.util.Locale;
import java.util.Objects;

/** A validated, separator-free ISBN-10 supplied by a provider. */
public record Isbn10(String value) {
  public Isbn10 {
    Objects.requireNonNull(value, "value");
    if (value.length() != 10 || !value.matches("[0-9]{9}[0-9X]") || !validChecksum(value))
      throw new IllegalArgumentException("ISBN-10 must be normalized and valid");
  }

  public static Isbn10 parse(String input) {
    return new Isbn10(normalize(input).toUpperCase(Locale.ROOT));
  }

  public Isbn13 toIsbn13() {
    String body = "978" + value.substring(0, 9);
    return new Isbn13(body + Isbn13.checkDigit(body));
  }

  private static boolean validChecksum(String value) {
    int sum = 0;
    for (int index = 0; index < 10; index++)
      sum += (10 - index) * (value.charAt(index) == 'X' ? 10 : value.charAt(index) - '0');
    return sum % 11 == 0;
  }

  private static String normalize(String input) {
    Objects.requireNonNull(input, "input");
    return input.replaceAll("[-\\s]", "");
  }
}
