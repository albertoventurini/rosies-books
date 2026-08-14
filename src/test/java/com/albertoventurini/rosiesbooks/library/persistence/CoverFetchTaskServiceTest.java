package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_FETCH_TASK;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoverFetchTaskServiceTest {

  @Inject DSLContext dsl;
  @Inject CoverFetchTaskService tasks;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void claimsAnEligibleTaskUsingPostgresJoinedTables() {
    UUID edition = UUID.randomUUID();
    UUID book = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, edition)
        .set(EDITION.ISBN_13, "9780306406157")
        .set(EDITION.TITLE, "Queued cover")
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, now)
        .set(EDITION.UPDATED_AT, now)
        .execute();
    dsl.insertInto(EDITION_AUTHOR)
        .set(EDITION_AUTHOR.EDITION_ID, edition)
        .set(EDITION_AUTHOR.POSITION, 0)
        .set(EDITION_AUTHOR.NAME, "Author")
        .execute();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, book)
        .set(USER_EDITION.USER_ID, DevelopmentUser.READER_ONE.currentUser().id().value())
        .set(USER_EDITION.EDITION_ID, edition)
        .set(USER_EDITION.STATE, "TO_READ")
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, "Queued cover")
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Author")
        .set(USER_EDITION.CREATED_AT, now)
        .set(USER_EDITION.UPDATED_AT, now)
        .execute();

    tasks.request(DevelopmentUser.READER_ONE.currentUser(), new UserEditionId(book));

    var claim = tasks.claim();

    assertTrue(claim.isPresent());
    assertEquals(edition, claim.orElseThrow().editionId());
  }

  @Test
  void marksATaskUnavailableAfterItsThirdFailedAttempt() {
    UUID edition = UUID.randomUUID();
    UUID book = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, edition)
        .set(EDITION.ISBN_13, "9780306406157")
        .set(EDITION.TITLE, "Unfetchable cover")
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, now)
        .set(EDITION.UPDATED_AT, now)
        .execute();
    dsl.insertInto(EDITION_AUTHOR)
        .set(EDITION_AUTHOR.EDITION_ID, edition)
        .set(EDITION_AUTHOR.POSITION, 0)
        .set(EDITION_AUTHOR.NAME, "Author")
        .execute();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, book)
        .set(USER_EDITION.USER_ID, DevelopmentUser.READER_ONE.currentUser().id().value())
        .set(USER_EDITION.EDITION_ID, edition)
        .set(USER_EDITION.STATE, "TO_READ")
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, "Unfetchable cover")
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Author")
        .set(USER_EDITION.CREATED_AT, now)
        .set(USER_EDITION.UPDATED_AT, now)
        .execute();
    tasks.request(DevelopmentUser.READER_ONE.currentUser(), new UserEditionId(book));
    var claim = tasks.claim().orElseThrow();
    dsl.update(COVER_FETCH_TASK)
        .set(COVER_FETCH_TASK.ATTEMPT_COUNT, 3)
        .where(COVER_FETCH_TASK.ID.eq(claim.taskId()))
        .execute();

    tasks.complete(
        claim.taskId(), new ProviderCoverPersistenceService.FetchOutcome.Retry(Optional.empty()));

    assertEquals(
        "NO_COVER",
        dsl.select(COVER_FETCH_TASK.STATUS)
            .from(COVER_FETCH_TASK)
            .where(COVER_FETCH_TASK.ID.eq(claim.taskId()))
            .fetchOne(COVER_FETCH_TASK.STATUS));
  }
}
