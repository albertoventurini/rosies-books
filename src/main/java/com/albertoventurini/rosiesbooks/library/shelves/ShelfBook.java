package com.albertoventurini.rosiesbooks.library.shelves;

import java.util.List;
import java.util.Objects;

/** The complete book data required to render a shelf row. */
public record ShelfBook(String title, List<String> authors) {

  public ShelfBook {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(authors, "authors");
    authors = List.copyOf(authors);
    if (title.isBlank()) {
      throw new IllegalArgumentException("Effective title must not be blank");
    }
    if (authors.isEmpty()
        || authors.stream().anyMatch(author -> author == null || author.isBlank())) {
      throw new IllegalArgumentException("Effective authors must contain only nonblank names");
    }
  }
}
