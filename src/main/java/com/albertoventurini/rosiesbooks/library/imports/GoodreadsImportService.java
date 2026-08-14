package com.albertoventurini.rosiesbooks.library.imports;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.persistence.CoverFetchTaskService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

/** Transactional owner-private Goodreads import and durable result lookup. */
@ApplicationScoped
class GoodreadsImportService {
  private static final Table<?> IMPORT = DSL.table(DSL.name("goodreads_import"));
  private static final Field<UUID> REQUEST_ID = DSL.field(DSL.name("request_id"), UUID.class);
  private static final Field<UUID> USER_ID = DSL.field(DSL.name("user_id"), UUID.class);
  private static final Field<Integer> IMPORTED =
      DSL.field(DSL.name("imported_count"), Integer.class);
  private static final Field<Integer> PRESENT =
      DSL.field(DSL.name("already_present_count"), Integer.class);
  private static final Field<Integer> READING = DSL.field(DSL.name("reading_count"), Integer.class);
  private static final Field<Integer> TO_READ = DSL.field(DSL.name("to_read_count"), Integer.class);
  private static final Field<Integer> FINISHED =
      DSL.field(DSL.name("finished_count"), Integer.class);
  private static final Field<OffsetDateTime> CREATED_AT =
      DSL.field(DSL.name("created_at"), OffsetDateTime.class);
  private final DSLContext dsl;
  private final Clock clock;
  private final CoverFetchTaskService coverTasks;
  private final ZoneId zone;

  GoodreadsImportService(DSLContext dsl, Clock clock, CoverFetchTaskService coverTasks) {
    this.dsl = dsl;
    this.clock = clock;
    this.coverTasks = coverTasks;
    this.zone = ZoneId.of("Africa/Johannesburg");
  }

  @Transactional
  GoodreadsImportResult importRows(
      CurrentUser owner, UUID requestId, List<GoodreadsCsvParser.GoodreadsRow> rows) {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(requestId);
    Objects.requireNonNull(rows);
    dsl.fetch(
        "select pg_advisory_xact_lock(hashtextextended(cast(? as text) || ':' || cast(? as text),"
            + " 0))",
        owner.id().value(),
        requestId);
    Optional<GoodreadsImportResult> previous = find(owner, requestId);
    if (previous.isPresent()) return previous.get();
    Instant now = Instant.now(clock);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    int imported = 0, present = 0, reading = 0, toRead = 0, finished = 0;
    List<UUID> queuedUserEditions = new ArrayList<>();
    for (int position = 0; position < rows.size(); position++) {
      GoodreadsCsvParser.GoodreadsRow row = rows.get(position);
      UUID editionId = resolveEdition(row, now);
      if (alreadyLinked(owner, editionId)) {
        present++;
        continue;
      }
      State state = state(row, today);
      UUID userEditionId =
          link(
              owner,
              editionId,
              row,
              state,
              row.addedOn() == null
                  ? now
                  : row.addedOn().atStartOfDay(zone).plusSeconds(position).toInstant());
      queuedUserEditions.add(userEditionId);
      imported++;
      switch (state.name) {
        case "READING" -> reading++;
        case "FINISHED" -> finished++;
        default -> toRead++;
      }
    }
    GoodreadsImportResult result =
        new GoodreadsImportResult(requestId, imported, present, reading, toRead, finished);
    dsl.insertInto(IMPORT)
        .columns(REQUEST_ID, USER_ID, IMPORTED, PRESENT, READING, TO_READ, FINISHED, CREATED_AT)
        .values(
            requestId, owner.id().value(), imported, present, reading, toRead, finished, atUtc(now))
        .execute();
    queuedUserEditions.forEach(
        userEditionId -> coverTasks.enqueueImport(owner, requestId, userEditionId));
    return result;
  }

  Optional<GoodreadsImportResult> find(CurrentUser owner, UUID requestId) {
    return dsl.select(REQUEST_ID, IMPORTED, PRESENT, READING, TO_READ, FINISHED)
        .from(IMPORT)
        .where(USER_ID.eq(owner.id().value()).and(REQUEST_ID.eq(requestId)))
        .fetchOptional(
            row ->
                new GoodreadsImportResult(
                    row.get(REQUEST_ID),
                    row.get(IMPORTED),
                    row.get(PRESENT),
                    row.get(READING),
                    row.get(TO_READ),
                    row.get(FINISHED)));
  }

  private UUID resolveEdition(GoodreadsCsvParser.GoodreadsRow row, Instant now) {
    UUID candidate = UUID.randomUUID();
    var insert =
        dsl.insertInto(EDITION)
            .set(EDITION.ID, candidate)
            .set(EDITION.ISBN_10, (String) null)
            .set(EDITION.ISBN_13, row.isbn13().map(Isbn13::value).orElse(null))
            .set(EDITION.PROVIDER_NAME, (String) null)
            .set(EDITION.PROVIDER_EDITION_ID, (String) null)
            .set(EDITION.TITLE, row.title())
            .set(EDITION.SUBTITLE, (String) null)
            .set(EDITION.FORMAT, row.format().orElse(null))
            .set(EDITION.PUBLISHER, row.publisher().orElse(null))
            .set(EDITION.PUBLICATION_YEAR, row.publicationYear())
            .set(EDITION.PUBLICATION_MONTH, (Integer) null)
            .set(EDITION.PUBLICATION_DAY, (Integer) null)
            .set(EDITION.PAGE_COUNT, row.pageCount())
            .set(EDITION.LANGUAGE, (String) null)
            .set(EDITION.DESCRIPTION, (String) null)
            .set(EDITION.COVER_ASSET_ID, (UUID) null)
            .set(EDITION.METADATA_ORIGIN, "MANUAL")
            .set(EDITION.CREATED_AT, atUtc(now))
            .set(EDITION.UPDATED_AT, atUtc(now));
    UUID resolved =
        row.isbn13().isEmpty()
            ? insert.returning(EDITION.ID).fetchOne(EDITION.ID)
            : insert
                .onConflict(EDITION.ISBN_13)
                .doNothing()
                .returning(EDITION.ID)
                .fetchOptional(EDITION.ID)
                .orElseGet(
                    () ->
                        dsl.select(EDITION.ID)
                            .from(EDITION)
                            .where(EDITION.ISBN_13.eq(row.isbn13().orElseThrow().value()))
                            .fetchSingle(EDITION.ID));
    if (candidate.equals(resolved))
      for (int i = 0; i < row.authors().size(); i++)
        dsl.insertInto(EDITION_AUTHOR)
            .set(EDITION_AUTHOR.EDITION_ID, resolved)
            .set(EDITION_AUTHOR.POSITION, i)
            .set(EDITION_AUTHOR.NAME, row.authors().get(i))
            .execute();
    return resolved;
  }

  private boolean alreadyLinked(CurrentUser owner, UUID editionId) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(USER_EDITION)
            .where(
                USER_EDITION
                    .USER_ID
                    .eq(owner.id().value())
                    .and(USER_EDITION.EDITION_ID.eq(editionId))));
  }

  private UUID link(
      CurrentUser owner,
      UUID editionId,
      GoodreadsCsvParser.GoodreadsRow row,
      State state,
      Instant createdAt) {
    String authors =
        String.join(
            " ",
            dsl.select(EDITION_AUTHOR.NAME)
                .from(EDITION_AUTHOR)
                .where(EDITION_AUTHOR.EDITION_ID.eq(editionId))
                .orderBy(EDITION_AUTHOR.POSITION)
                .fetch(EDITION_AUTHOR.NAME));
    UUID userEditionId = UUID.randomUUID();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, userEditionId)
        .set(USER_EDITION.USER_ID, owner.id().value())
        .set(USER_EDITION.EDITION_ID, editionId)
        .set(USER_EDITION.STATE, state.name)
        .set(USER_EDITION.STARTED_ON, state.startedOn)
        .set(USER_EDITION.FINISHED_ON, state.finishedOn)
        .set(USER_EDITION.PRIVATE_NOTES, row.notes().orElse(null))
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, row.title())
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, authors)
        .set(USER_EDITION.CREATED_AT, atUtc(createdAt))
        .set(USER_EDITION.UPDATED_AT, atUtc(createdAt))
        .set(USER_EDITION.VERSION, 0L)
        .execute();
    return userEditionId;
  }

  private static State state(GoodreadsCsvParser.GoodreadsRow row, LocalDate today) {
    return switch (row.shelf()) {
      case "read" ->
          new State(
              "FINISHED",
              null,
              row.readOn() != null ? row.readOn() : row.addedOn() != null ? row.addedOn() : today);
      case "currently-reading" ->
          new State("READING", row.addedOn() == null ? today : row.addedOn(), null);
      default -> new State("TO_READ", null, null);
    };
  }

  private static OffsetDateTime atUtc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private record State(String name, LocalDate startedOn, LocalDate finishedOn) {}

  record GoodreadsImportResult(
      UUID requestId,
      int importedCount,
      int alreadyPresentCount,
      int readingCount,
      int toReadCount,
      int finishedCount) {}
}
