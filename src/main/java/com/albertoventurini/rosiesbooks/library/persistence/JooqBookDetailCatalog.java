package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.api.BookDetailCatalog;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.EffectiveMetadataResolver;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

@ApplicationScoped
public class JooqBookDetailCatalog implements BookDetailCatalog {

  private final DSLContext dsl;
  private final MetadataOverrideRepository overrides;

  JooqBookDetailCatalog(DSLContext dsl, MetadataOverrideRepository overrides) {
    this.dsl = dsl;
    this.overrides = overrides;
  }

  @Override
  public Optional<BookDetail> find(CurrentUser owner, UserEditionId id) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(id, "id");
    return dsl.select(
            EDITION.ID,
            EDITION.ISBN_10,
            EDITION.ISBN_13,
            EDITION.TITLE,
            EDITION.SUBTITLE,
            EDITION.FORMAT,
            EDITION.PUBLISHER,
            EDITION.PUBLICATION_YEAR,
            EDITION.PUBLICATION_MONTH,
            EDITION.PUBLICATION_DAY,
            EDITION.PAGE_COUNT,
            EDITION.LANGUAGE,
            EDITION.DESCRIPTION,
            EDITION.COVER_ASSET_ID,
            EDITION.PROVIDER_NAME,
            EDITION.COVER_LAST_OUTCOME,
            COVER_ASSET.SHA256,
            USER_EDITION.STATE,
            USER_EDITION.STARTED_ON,
            USER_EDITION.FINISHED_ON,
            USER_EDITION.PRIVATE_NOTES)
        .from(USER_EDITION)
        .join(EDITION)
        .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
        .leftJoin(COVER_ASSET)
        .on(COVER_ASSET.ID.eq(EDITION.COVER_ASSET_ID))
        .where(USER_EDITION.ID.eq(id.value()).and(USER_EDITION.USER_ID.eq(owner.id().value())))
        .fetchOptional(
            row -> {
              EditionMetadata canonical =
                  new EditionMetadata(
                      row.get(EDITION.TITLE),
                      Optional.ofNullable(row.get(EDITION.SUBTITLE)),
                      authors(row.get(EDITION.ID)),
                      Optional.ofNullable(row.get(EDITION.FORMAT)),
                      Optional.ofNullable(row.get(EDITION.ISBN_10)).map(Isbn10::parse),
                      Optional.ofNullable(row.get(EDITION.ISBN_13)).map(Isbn13::parse),
                      Optional.ofNullable(row.get(EDITION.PUBLISHER)),
                      publicationDate(row),
                      Optional.ofNullable(row.get(EDITION.PAGE_COUNT)),
                      Optional.ofNullable(row.get(EDITION.LANGUAGE)),
                      Optional.ofNullable(row.get(EDITION.DESCRIPTION)));
              EditionMetadata metadata =
                  EffectiveMetadataResolver.resolve(
                      canonical, overrides.find(owner, id).orElse(MetadataOverrides.none()));
              ReadingState state =
                  state(
                      row.get(USER_EDITION.STATE),
                      row.get(USER_EDITION.STARTED_ON),
                      row.get(USER_EDITION.FINISHED_ON));
              return new BookDetail(
                  metadata,
                  state,
                  row.get(USER_EDITION.PRIVATE_NOTES),
                  shelf(row.get(USER_EDITION.STATE)),
                  row.get(COVER_ASSET.SHA256),
                  row.get(COVER_ASSET.SHA256) == null
                      && ("FAILED".equals(row.get(EDITION.COVER_LAST_OUTCOME))
                          || (row.get(EDITION.PROVIDER_NAME) != null
                              && row.get(EDITION.ISBN_13) != null)));
            });
  }

  @Override
  public Optional<StoredCover> findCover(CurrentUser owner, UserEditionId id) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(id, "id");
    return dsl.select(COVER_ASSET.CONTENT, COVER_ASSET.MIME_TYPE)
        .from(USER_EDITION)
        .join(EDITION)
        .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
        .join(COVER_ASSET)
        .on(COVER_ASSET.ID.eq(EDITION.COVER_ASSET_ID))
        .where(USER_EDITION.ID.eq(id.value()).and(USER_EDITION.USER_ID.eq(owner.id().value())))
        .fetchOptional(
            row -> new StoredCover(row.get(COVER_ASSET.CONTENT), row.get(COVER_ASSET.MIME_TYPE)));
  }

  private List<String> authors(java.util.UUID editionId) {
    return dsl.select(EDITION_AUTHOR.NAME)
        .from(EDITION_AUTHOR)
        .where(EDITION_AUTHOR.EDITION_ID.eq(editionId))
        .orderBy(EDITION_AUTHOR.POSITION)
        .fetch(EDITION_AUTHOR.NAME);
  }

  private static Optional<PartialPublicationDate> publicationDate(org.jooq.Record row) {
    Integer year = row.get(EDITION.PUBLICATION_YEAR);
    Integer month = row.get(EDITION.PUBLICATION_MONTH);
    Integer day = row.get(EDITION.PUBLICATION_DAY);
    return year == null
        ? Optional.empty()
        : Optional.of(new PartialPublicationDate(year, month, day));
  }

  private static ReadingState state(String state, LocalDate startedOn, LocalDate finishedOn) {
    return switch (state) {
      case "TO_READ" -> new ToRead();
      case "READING" -> new Reading(startedOn);
      case "FINISHED" -> new Finished(Optional.ofNullable(startedOn), finishedOn);
      default -> throw new IllegalStateException("Unsupported persisted reading state: " + state);
    };
  }

  private static Shelf shelf(String state) {
    return switch (state) {
      case "TO_READ" -> Shelf.TO_READ;
      case "READING" -> Shelf.READING;
      case "FINISHED" -> Shelf.FINISHED;
      default -> throw new IllegalStateException("Unsupported persisted reading state: " + state);
    };
  }
}
