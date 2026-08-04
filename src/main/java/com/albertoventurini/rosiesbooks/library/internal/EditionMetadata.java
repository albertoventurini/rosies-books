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

    if (title.isBlank()) {
      throw new IllegalArgumentException("Edition title must not be blank");
    }
    requireLength(title, EditionMetadataLimits.TITLE, "Edition title");
    if (authors.size() < EditionMetadataLimits.MIN_AUTHORS
        || authors.size() > EditionMetadataLimits.MAX_AUTHORS) {
      throw new IllegalArgumentException("Edition must have between 1 and 20 authors");
    }
    for (String author : authors) {
      Objects.requireNonNull(author, "author");
      if (author.isBlank()) {
        throw new IllegalArgumentException("Edition authors must not be blank");
      }
      requireLength(author, EditionMetadataLimits.AUTHOR, "Edition author");
    }
    requireOptionalLength(subtitle, EditionMetadataLimits.SHORT_TEXT, "Edition subtitle");
    requireOptionalLength(format, EditionMetadataLimits.SHORT_TEXT, "Edition format");
    requireOptionalLength(publisher, EditionMetadataLimits.SHORT_TEXT, "Edition publisher");
    requireOptionalLength(language, EditionMetadataLimits.SHORT_TEXT, "Edition language");
    requireOptionalLength(description, EditionMetadataLimits.DESCRIPTION, "Edition description");
    if (pageCount
        .filter(
            value ->
                value < EditionMetadataLimits.MIN_PAGE_COUNT
                    || value > EditionMetadataLimits.MAX_PAGE_COUNT)
        .isPresent()) {
      throw new IllegalArgumentException("Edition page count must be between 1 and 1000000");
    }
  }

  private static void requireOptionalLength(Optional<String> value, int maximum, String fieldName) {
    value.ifPresent(text -> requireLength(text, maximum, fieldName));
  }

  private static void requireLength(String value, int maximum, String fieldName) {
    if (value.length() > maximum) {
      throw new IllegalArgumentException(fieldName + " exceeds its supported length");
    }
  }
}
