package com.albertoventurini.rosiesbooks.library.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-independent bibliographic metadata. */
public record EditionMetadata(
    String title,
    Optional<String> subtitle,
    List<String> authors,
    Optional<String> format,
    Optional<Isbn10> isbn10,
    Optional<Isbn13> isbn13,
    Optional<String> publisher,
    Optional<PartialPublicationDate> publicationDate,
    Optional<Integer> pageCount,
    Optional<String> language,
    Optional<String> description) {

  public EditionMetadata {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(subtitle, "subtitle");
    authors = List.copyOf(authors);
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(isbn10, "isbn10");
    Objects.requireNonNull(isbn13, "isbn13");
    Objects.requireNonNull(publisher, "publisher");
    Objects.requireNonNull(publicationDate, "publicationDate");
    Objects.requireNonNull(pageCount, "pageCount");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(description, "description");
  }
}
