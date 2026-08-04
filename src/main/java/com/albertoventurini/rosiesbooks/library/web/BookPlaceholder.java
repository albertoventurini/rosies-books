package com.albertoventurini.rosiesbooks.library.web;

import java.util.List;
import java.util.Objects;

record BookPlaceholder(String title, String authorsText, String themeClass) {

  private static final List<String> THEME_CLASSES =
      List.of(
          "placeholder-theme-1",
          "placeholder-theme-2",
          "placeholder-theme-3",
          "placeholder-theme-4",
          "placeholder-theme-5",
          "placeholder-theme-6");

  static BookPlaceholder from(String title, List<String> authors) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(authors, "authors");
    List<String> orderedAuthors = List.copyOf(authors);
    if (title.isBlank()) {
      throw new IllegalArgumentException("Effective title must not be blank");
    }
    if (orderedAuthors.isEmpty()
        || orderedAuthors.stream().anyMatch(author -> author == null || author.isBlank())) {
      throw new IllegalArgumentException("Effective authors must contain only nonblank names");
    }
    int theme = Math.floorMod(Objects.hash(title, orderedAuthors), THEME_CLASSES.size());
    return new BookPlaceholder(title, String.join(", ", orderedAuthors), THEME_CLASSES.get(theme));
  }

  static List<String> themeClasses() {
    return THEME_CLASSES;
  }
}
