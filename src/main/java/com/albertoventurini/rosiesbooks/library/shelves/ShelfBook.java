package com.albertoventurini.rosiesbooks.library.shelves;

import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import java.util.List;
import java.util.Objects;

/** The complete book data required to render a shelf row. */
public record ShelfBook(UserEditionId userEditionId, String title, List<String> authors) {

  public ShelfBook {
    Objects.requireNonNull(userEditionId, "userEditionId");
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
