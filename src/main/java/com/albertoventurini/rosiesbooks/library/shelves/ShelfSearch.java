package com.albertoventurini.rosiesbooks.library.shelves;

import java.util.Optional;

/** A validated, owner-library search query. */
public sealed interface ShelfSearch permits ShelfSearch.Text, ShelfSearch.Isbn {

  String input();

  static Optional<ShelfSearch> parse(String submitted) {
    String input = submitted == null ? "" : submitted.trim();
    String isbnDigits = input.replaceAll("[ -]", "");
    if (isbnDigits.matches("[0-9]{6,}")) {
      return Optional.of(new Isbn(input, isbnDigits));
    }
    if (input.codePoints().filter(Character::isLetter).count() >= 3) {
      return Optional.of(new Text(input));
    }
    return Optional.empty();
  }

  record Text(String input) implements ShelfSearch {}

  record Isbn(String input, String digits) implements ShelfSearch {}
}
