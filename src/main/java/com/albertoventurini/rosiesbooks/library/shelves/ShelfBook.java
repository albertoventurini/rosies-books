package com.albertoventurini.rosiesbooks.library.shelves;

import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The complete book data required to present a book on a shelf. */
public record ShelfBook(
    UserEditionId userEditionId,
    String title,
    List<String> authors,
    ReadingState readingState,
    Instant createdAt) {

  public ShelfBook {
    Objects.requireNonNull(userEditionId, "userEditionId");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(authors, "authors");
    Objects.requireNonNull(readingState, "readingState");
    Objects.requireNonNull(createdAt, "createdAt");
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
