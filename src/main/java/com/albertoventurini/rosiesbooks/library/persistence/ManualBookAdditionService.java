package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.CanonicalIsbns;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jooq.DSLContext;

/** Atomically creates or reuses the canonical and owner-scoped rows for a manual addition. */
@ApplicationScoped
public class ManualBookAdditionService {

  private final DSLContext dsl;
  private final Clock clock;

  ManualBookAdditionService(DSLContext dsl, Clock clock) {
    this.dsl = dsl;
    this.clock = clock;
  }

  @Transactional
  public AddedBook add(
      CurrentUser owner, UUID requestId, EditionMetadata metadata, ReadingState initialState) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(metadata, "metadata");
    Objects.requireNonNull(initialState, "initialState");

    lockRetry(owner, requestId);
    AddedBook repeated = findByRequest(owner, requestId);
    if (repeated != null) {
      return repeated;
    }

    UUID editionId = resolveEdition(metadata);
    AddedBook linked = findByEdition(owner, editionId);
    if (linked != null) {
      return linked;
    }
    return link(owner, requestId, editionId, initialState);
  }

  private void lockRetry(CurrentUser owner, UUID requestId) {
    dsl.fetch(
        "select pg_advisory_xact_lock("
            + "hashtextextended(cast(? as text) || ':' || cast(? as text), 0))",
        owner.id().value(),
        requestId);
  }

  private AddedBook findByRequest(CurrentUser owner, UUID requestId) {
    return dsl.select(USER_EDITION.ID, USER_EDITION.STATE)
        .from(USER_EDITION)
        .where(
            USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.REQUEST_ID.eq(requestId)))
        .fetchOne(
            row ->
                new AddedBook(
                    new UserEditionId(row.get(USER_EDITION.ID)), row.get(USER_EDITION.STATE)));
  }

  private AddedBook findByEdition(CurrentUser owner, UUID editionId) {
    return dsl.select(USER_EDITION.ID, USER_EDITION.STATE)
        .from(USER_EDITION)
        .where(
            USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.EDITION_ID.eq(editionId)))
        .fetchOne(
            row ->
                new AddedBook(
                    new UserEditionId(row.get(USER_EDITION.ID)), row.get(USER_EDITION.STATE)));
  }

  private UUID resolveEdition(EditionMetadata metadata) {
    CanonicalIsbns canonicalIsbns = new CanonicalIsbns(metadata.isbn10(), metadata.isbn13());
    Isbn13 isbn13 = canonicalIsbns.isbn13().orElse(null);
    UUID candidateId = UUID.randomUUID();
    Instant now = Instant.now(clock);
    PartialPublicationDate publicationDate =
        metadata.publicationDate().orElse(PartialPublicationDate.unknown());

    var insert =
        dsl.insertInto(EDITION)
            .set(EDITION.ID, candidateId)
            .set(EDITION.ISBN_10, canonicalIsbns.isbn10().map(Isbn10::value).orElse(null))
            .set(EDITION.ISBN_13, isbn13 == null ? null : isbn13.value())
            .set(EDITION.PROVIDER_NAME, (String) null)
            .set(EDITION.PROVIDER_EDITION_ID, (String) null)
            .set(EDITION.TITLE, metadata.title())
            .set(EDITION.SUBTITLE, metadata.subtitle().orElse(null))
            .set(EDITION.FORMAT, metadata.format().orElse(null))
            .set(EDITION.PUBLISHER, metadata.publisher().orElse(null))
            .set(EDITION.PUBLICATION_YEAR, publicationDate.year())
            .set(EDITION.PUBLICATION_MONTH, publicationDate.month())
            .set(EDITION.PUBLICATION_DAY, publicationDate.day())
            .set(EDITION.PAGE_COUNT, metadata.pageCount().orElse(null))
            .set(EDITION.LANGUAGE, metadata.language().orElse(null))
            .set(EDITION.DESCRIPTION, metadata.description().orElse(null))
            .set(EDITION.COVER_ASSET_ID, (UUID) null)
            .set(EDITION.METADATA_ORIGIN, MetadataOrigin.MANUAL.name())
            .set(EDITION.CREATED_AT, atUtc(now))
            .set(EDITION.UPDATED_AT, atUtc(now));

    UUID resolvedId;
    if (isbn13 == null) {
      resolvedId = insert.returning(EDITION.ID).fetchOne(EDITION.ID);
    } else {
      resolvedId =
          insert
              .onConflict(EDITION.ISBN_13)
              .doNothing()
              .returning(EDITION.ID)
              .fetchOptional(EDITION.ID)
              .orElseGet(
                  () ->
                      dsl.select(EDITION.ID)
                          .from(EDITION)
                          .where(EDITION.ISBN_13.eq(isbn13.value()))
                          .fetchSingle(EDITION.ID));
    }
    if (resolvedId.equals(candidateId)) {
      insertAuthors(resolvedId, metadata.authors());
    }
    return resolvedId;
  }

  private void insertAuthors(UUID editionId, List<String> authors) {
    for (int position = 0; position < authors.size(); position++) {
      dsl.insertInto(EDITION_AUTHOR)
          .set(EDITION_AUTHOR.EDITION_ID, editionId)
          .set(EDITION_AUTHOR.POSITION, position)
          .set(EDITION_AUTHOR.NAME, authors.get(position))
          .execute();
    }
  }

  private AddedBook link(
      CurrentUser owner, UUID requestId, UUID editionId, ReadingState initialState) {
    StateColumns state = columns(initialState);
    var canonical =
        dsl.select(EDITION.TITLE).from(EDITION).where(EDITION.ID.eq(editionId)).fetchSingle();
    String authors =
        String.join(
            " ",
            dsl.select(EDITION_AUTHOR.NAME)
                .from(EDITION_AUTHOR)
                .where(EDITION_AUTHOR.EDITION_ID.eq(editionId))
                .orderBy(EDITION_AUTHOR.POSITION)
                .fetch(EDITION_AUTHOR.NAME));
    Instant now = Instant.now(clock);
    UUID userEditionId = UUID.randomUUID();

    AddedBook inserted =
        dsl.insertInto(USER_EDITION)
            .set(USER_EDITION.ID, userEditionId)
            .set(USER_EDITION.USER_ID, owner.id().value())
            .set(USER_EDITION.EDITION_ID, editionId)
            .set(USER_EDITION.REQUEST_ID, requestId)
            .set(USER_EDITION.STATE, state.state())
            .set(USER_EDITION.STARTED_ON, state.startedOn())
            .set(USER_EDITION.FINISHED_ON, state.finishedOn())
            .set(USER_EDITION.PRIVATE_NOTES, (String) null)
            .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, canonical.value1())
            .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, authors)
            .set(USER_EDITION.CREATED_AT, atUtc(now))
            .set(USER_EDITION.UPDATED_AT, atUtc(now))
            .onConflict(USER_EDITION.USER_ID, USER_EDITION.EDITION_ID)
            .doNothing()
            .returning(USER_EDITION.ID, USER_EDITION.STATE)
            .fetchOne(
                row ->
                    new AddedBook(
                        new UserEditionId(row.get(USER_EDITION.ID)), row.get(USER_EDITION.STATE)));
    return inserted == null ? Objects.requireNonNull(findByEdition(owner, editionId)) : inserted;
  }

  private static OffsetDateTime atUtc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static StateColumns columns(ReadingState state) {
    return switch (state) {
      case ToRead ignored -> new StateColumns("TO_READ", null, null);
      case Reading reading -> new StateColumns("READING", reading.startedOn(), null);
      case Finished finished ->
          new StateColumns("FINISHED", finished.startedOn().orElse(null), finished.finishedOn());
    };
  }

  public record AddedBook(UserEditionId id, String state) {
    public AddedBook {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(state, "state");
    }
  }

  private record StateColumns(String state, LocalDate startedOn, LocalDate finishedOn) {}
}
