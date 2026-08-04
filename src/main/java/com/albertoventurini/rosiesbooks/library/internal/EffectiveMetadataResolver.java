package com.albertoventurini.rosiesbooks.library.internal;

import java.util.List;
import java.util.Optional;

/** Applies private field choices to canonical metadata without infrastructure dependencies. */
public final class EffectiveMetadataResolver {

  private EffectiveMetadataResolver() {}

  public static EditionMetadata resolve(EditionMetadata canonical, MetadataOverrides overrides) {
    String title = required(canonical.title(), overrides.title(), "title");
    List<String> authors = required(canonical.authors(), overrides.authors(), "authors");
    if (title.isBlank()) {
      throw new IllegalArgumentException("Effective title must not be blank");
    }
    if (authors.isEmpty() || authors.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("Effective authors must contain only nonblank names");
    }

    Optional<Integer> pageCount = optional(canonical.pageCount(), overrides.pageCount());
    if (pageCount.filter(value -> value <= 0).isPresent()) {
      throw new IllegalArgumentException("Effective page count must be positive");
    }

    return new EditionMetadata(
        title,
        optional(canonical.subtitle(), overrides.subtitle()),
        authors,
        optional(canonical.format(), overrides.format()),
        optional(canonical.isbn10(), overrides.isbn10()),
        optional(canonical.isbn13(), overrides.isbn13()),
        optional(canonical.publisher(), overrides.publisher()),
        optional(canonical.publicationDate(), overrides.publicationDate()),
        pageCount,
        optional(canonical.language(), overrides.language()),
        optional(canonical.description(), overrides.description()));
  }

  private static <T> T required(T canonical, MetadataOverride<T> override, String field) {
    if (override.isInherited()) {
      return canonical;
    }
    return override
        .value()
        .orElseThrow(() -> new IllegalArgumentException("Effective " + field + " is required"));
  }

  private static <T> Optional<T> optional(Optional<T> canonical, MetadataOverride<T> override) {
    return override.isInherited() ? canonical : override.value();
  }
}
