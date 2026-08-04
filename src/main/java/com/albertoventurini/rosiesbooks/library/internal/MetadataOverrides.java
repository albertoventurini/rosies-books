package com.albertoventurini.rosiesbooks.library.internal;

import java.util.List;
import java.util.Objects;

/** A complete immutable snapshot of all private metadata choices. */
public record MetadataOverrides(
    MetadataOverride<String> title,
    MetadataOverride<String> subtitle,
    MetadataOverride<List<String>> authors,
    MetadataOverride<String> format,
    MetadataOverride<Isbn10> isbn10,
    MetadataOverride<Isbn13> isbn13,
    MetadataOverride<String> publisher,
    MetadataOverride<PartialPublicationDate> publicationDate,
    MetadataOverride<Integer> pageCount,
    MetadataOverride<String> language,
    MetadataOverride<String> description) {

  public MetadataOverrides {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(subtitle, "subtitle");
    Objects.requireNonNull(authors, "authors");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(isbn10, "isbn10");
    Objects.requireNonNull(isbn13, "isbn13");
    Objects.requireNonNull(publisher, "publisher");
    Objects.requireNonNull(publicationDate, "publicationDate");
    Objects.requireNonNull(pageCount, "pageCount");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(description, "description");
    if (authors.value().isPresent()) {
      authors = MetadataOverride.value(List.copyOf(authors.value().orElseThrow()));
    }
  }

  public static MetadataOverrides none() {
    return new MetadataOverrides(
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited(),
        MetadataOverride.inherited());
  }

  public MetadataOverrides withTitle(MetadataOverride<String> replacement) {
    return new MetadataOverrides(
        replacement,
        subtitle,
        authors,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }

  public MetadataOverrides withAuthors(MetadataOverride<List<String>> replacement) {
    return new MetadataOverrides(
        title,
        subtitle,
        replacement,
        format,
        isbn10,
        isbn13,
        publisher,
        publicationDate,
        pageCount,
        language,
        description);
  }
}
