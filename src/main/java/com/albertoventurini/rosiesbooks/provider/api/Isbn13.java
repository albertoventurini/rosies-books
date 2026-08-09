package com.albertoventurini.rosiesbooks.provider.api;

import java.util.Objects;

/** A validated, separator-free ISBN-13 passed to a provider lookup. */
public record Isbn13(String value) {
  public Isbn13 {
    Objects.requireNonNull(value, "value");
    if (!value.matches("97[89][0-9]{10}")
        || value.charAt(12) != checkDigit(value.substring(0, 12))) {
      throw new IllegalArgumentException("ISBN-13 must be normalized and valid");
    }
  }

  private static char checkDigit(String twelveDigits) {
    int sum = 0;
    for (int index = 0; index < twelveDigits.length(); index++) {
      sum += (twelveDigits.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
    }
    return (char) ('0' + (10 - sum % 10) % 10);
  }
}
