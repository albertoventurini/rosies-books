package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

enum LibraryLayout {
  COVER_CARD,
  COMPACT_LIST
}

enum MetadataOrigin {
  MANUAL,
  PROVIDER
}

enum ReadingState {
  TO_READ,
  READING,
  FINISHED
}

record Edition(
    EditionId id,
    String isbn10,
    String isbn13,
    String providerName,
    String providerEditionId,
    String title,
    String subtitle,
    List<String> authors,
    String format,
    String publisher,
    PartialPublicationDate publicationDate,
    Integer pageCount,
    String language,
    String description,
    UUID coverAssetId,
    MetadataOrigin metadataOrigin,
    Instant createdAt,
    Instant updatedAt) {

  Edition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(publicationDate, "publicationDate");
    Objects.requireNonNull(metadataOrigin, "metadataOrigin");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    authors = List.copyOf(authors);
    if (authors.isEmpty()) {
      throw new IllegalArgumentException("A canonical edition requires at least one author");
    }
  }
}

record UserEdition(
    UserEditionId id,
    EditionId editionId,
    ReadingState state,
    LocalDate startedOn,
    LocalDate finishedOn,
    String privateNotes,
    Instant createdAt,
    Instant updatedAt) {}

record OverrideValue<T>(boolean overridden, T value) {

  OverrideValue {
    if (!overridden && value != null) {
      throw new IllegalArgumentException("An inherited field cannot contain an override value");
    }
  }

  static <T> OverrideValue<T> inherited() {
    return new OverrideValue<>(false, null);
  }

  static <T> OverrideValue<T> overridden(T value) {
    return new OverrideValue<>(true, value);
  }
}

record MetadataOverrides(
    OverrideValue<String> title,
    OverrideValue<String> subtitle,
    OverrideValue<List<String>> authors,
    OverrideValue<String> format,
    OverrideValue<String> isbn10,
    OverrideValue<String> isbn13,
    OverrideValue<String> publisher,
    OverrideValue<PartialPublicationDate> publicationDate,
    OverrideValue<Integer> pageCount,
    OverrideValue<String> language,
    OverrideValue<String> description) {

  MetadataOverrides {
    if (authors.value() != null) {
      authors = new OverrideValue<>(authors.overridden(), List.copyOf(authors.value()));
    }
  }
}
