package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;

import com.albertoventurini.rosiesbooks.library.internal.CanonicalIsbns;
import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

@ApplicationScoped
class EditionRepository {

  private final DSLContext dsl;

  EditionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  void create(Edition edition) {
    if (edition.authors().isEmpty()) {
      throw new IllegalArgumentException("A canonical edition requires at least one author");
    }
    try {
      dsl.insertInto(EDITION)
          .set(EDITION.ID, edition.id().value())
          .set(EDITION.ISBN_10, edition.isbns().isbn10().map(Isbn10::value).orElse(null))
          .set(EDITION.ISBN_13, edition.isbns().isbn13().map(Isbn13::value).orElse(null))
          .set(EDITION.PROVIDER_NAME, edition.providerName())
          .set(EDITION.PROVIDER_EDITION_ID, edition.providerEditionId())
          .set(EDITION.TITLE, edition.title())
          .set(EDITION.SUBTITLE, edition.subtitle())
          .set(EDITION.FORMAT, edition.format())
          .set(EDITION.PUBLISHER, edition.publisher())
          .set(EDITION.PUBLICATION_YEAR, edition.publicationDate().year())
          .set(EDITION.PUBLICATION_MONTH, edition.publicationDate().month())
          .set(EDITION.PUBLICATION_DAY, edition.publicationDate().day())
          .set(EDITION.PAGE_COUNT, edition.pageCount())
          .set(EDITION.LANGUAGE, edition.language())
          .set(EDITION.DESCRIPTION, edition.description())
          .set(EDITION.COVER_ASSET_ID, edition.coverAssetId())
          .set(EDITION.METADATA_ORIGIN, edition.metadataOrigin().name())
          .set(EDITION.CREATED_AT, atUtc(edition.createdAt()))
          .set(EDITION.UPDATED_AT, atUtc(edition.updatedAt()))
          .execute();
      for (int position = 0; position < edition.authors().size(); position++) {
        dsl.insertInto(EDITION_AUTHOR)
            .set(EDITION_AUTHOR.EDITION_ID, edition.id().value())
            .set(EDITION_AUTHOR.POSITION, position)
            .set(EDITION_AUTHOR.NAME, edition.authors().get(position))
            .execute();
      }
    } catch (DataAccessException failure) {
      if (PostgresConstraint.isUniqueViolation(failure, "edition_isbn13_key")) {
        throw new DuplicateIsbn13Exception(failure);
      }
      if (PostgresConstraint.isUniqueViolation(failure, "edition_provider_identity_key")) {
        throw new DuplicateProviderEditionException(failure);
      }
      throw failure;
    }
  }

  Optional<Edition> find(EditionId id) {
    return dsl.selectFrom(EDITION)
        .where(EDITION.ID.eq(id.value()))
        .fetchOptional(
            row ->
                new Edition(
                    new EditionId(row.get(EDITION.ID)),
                    canonicalIsbns(row.get(EDITION.ISBN_10), row.get(EDITION.ISBN_13)),
                    row.get(EDITION.PROVIDER_NAME),
                    row.get(EDITION.PROVIDER_EDITION_ID),
                    row.get(EDITION.TITLE),
                    row.get(EDITION.SUBTITLE),
                    dsl.select(EDITION_AUTHOR.NAME)
                        .from(EDITION_AUTHOR)
                        .where(EDITION_AUTHOR.EDITION_ID.eq(id.value()))
                        .orderBy(EDITION_AUTHOR.POSITION)
                        .fetch(EDITION_AUTHOR.NAME),
                    row.get(EDITION.FORMAT),
                    row.get(EDITION.PUBLISHER),
                    new PartialPublicationDate(
                        row.get(EDITION.PUBLICATION_YEAR),
                        row.get(EDITION.PUBLICATION_MONTH),
                        row.get(EDITION.PUBLICATION_DAY)),
                    row.get(EDITION.PAGE_COUNT),
                    row.get(EDITION.LANGUAGE),
                    row.get(EDITION.DESCRIPTION),
                    row.get(EDITION.COVER_ASSET_ID),
                    MetadataOrigin.valueOf(row.get(EDITION.METADATA_ORIGIN)),
                    instant(row.get(EDITION.CREATED_AT)),
                    instant(row.get(EDITION.UPDATED_AT))));
  }

  Optional<Edition> findByIsbn(Isbn10 isbn) {
    return findByCanonicalIsbn13(isbn.toIsbn13());
  }

  Optional<Edition> findByIsbn(Isbn13 isbn) {
    return findByCanonicalIsbn13(isbn);
  }

  private Optional<Edition> findByCanonicalIsbn13(Isbn13 isbn) {
    return dsl.select(EDITION.ID)
        .from(EDITION)
        .where(EDITION.ISBN_13.eq(isbn.value()))
        .fetchOptional(record -> new EditionId(record.value1()))
        .flatMap(this::find);
  }

  boolean delete(EditionId id) {
    return dsl.deleteFrom(EDITION).where(EDITION.ID.eq(id.value())).execute() == 1;
  }

  private static OffsetDateTime atUtc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(OffsetDateTime value) {
    return value.toInstant();
  }

  private static CanonicalIsbns canonicalIsbns(String isbn10, String isbn13) {
    return new CanonicalIsbns(
        Optional.ofNullable(isbn10).map(Isbn10::parse),
        Optional.ofNullable(isbn13).map(Isbn13::parse));
  }
}
