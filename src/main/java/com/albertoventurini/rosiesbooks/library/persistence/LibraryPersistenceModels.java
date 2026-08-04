package com.albertoventurini.rosiesbooks.library.persistence;

import com.albertoventurini.rosiesbooks.library.internal.CanonicalIsbns;
import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

enum LibraryLayout {
  COVER_CARD,
  COMPACT_LIST
}

enum MetadataOrigin {
  MANUAL,
  PROVIDER
}

record Edition(
    EditionId id,
    CanonicalIsbns isbns,
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
    Objects.requireNonNull(isbns, "isbns");
    Objects.requireNonNull(publicationDate, "publicationDate");
    Objects.requireNonNull(metadataOrigin, "metadataOrigin");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    authors = List.copyOf(authors);
    if (authors.isEmpty()) {
      throw new IllegalArgumentException("A canonical edition requires at least one author");
    }
  }

  EditionMetadata metadata() {
    return new EditionMetadata(
        title,
        Optional.ofNullable(subtitle),
        authors,
        Optional.ofNullable(format),
        isbns.isbn10(),
        isbns.isbn13(),
        Optional.ofNullable(publisher),
        publicationDate.equals(PartialPublicationDate.unknown())
            ? Optional.empty()
            : Optional.of(publicationDate),
        Optional.ofNullable(pageCount),
        Optional.ofNullable(language),
        Optional.ofNullable(description));
  }
}

record UserEdition(
    UserEditionId id,
    EditionId editionId,
    ReadingState state,
    String privateNotes,
    Instant createdAt,
    Instant updatedAt) {

  UserEdition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(editionId, "editionId");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }
}
