package com.albertoventurini.rosiesbooks.provider.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Normalized data for one provider-selected concrete edition. */
public record SelectedEdition(
    String providerName,
    String providerEditionId,
    String title,
    Optional<String> subtitle,
    List<String> authors,
    Optional<String> format,
    Optional<String> publisher,
    Optional<Integer> publicationYear,
    Optional<Integer> pageCount,
    Optional<String> language,
    Optional<String> description,
    Optional<TrustedCoverReference> cover) {
  public SelectedEdition {
    if (providerName == null
        || providerName.isBlank()
        || providerEditionId == null
        || providerEditionId.isBlank()
        || title == null
        || title.isBlank())
      throw new IllegalArgumentException("Edition identity and title are required");
    Objects.requireNonNull(authors, "authors");
    if (authors.stream().anyMatch(author -> author == null || author.isBlank()))
      throw new IllegalArgumentException("authors must be nonblank");
    authors = List.copyOf(authors);
    Objects.requireNonNull(subtitle, "subtitle");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(publisher, "publisher");
    Objects.requireNonNull(publicationYear, "publicationYear");
    Objects.requireNonNull(pageCount, "pageCount");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(cover, "cover");
  }
}
