package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_AUTHOR_OVERRIDE;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_METADATA_OVERRIDE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import com.albertoventurini.rosiesbooks.library.api.BookDeletionService;
import com.albertoventurini.rosiesbooks.library.api.BookDeletionService.DeletionStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BookDeletionServiceTest {

  private static final CurrentUser FIRST = DevelopmentUser.READER_ONE.currentUser();
  private static final CurrentUser SECOND = DevelopmentUser.READER_TWO.currentUser();

  @Inject BookDeletionService deletions;
  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dropFailureTrigger();
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @AfterEach
  void dropFailureTrigger() {
    dsl.execute("drop trigger if exists test_deletion_failure on user_edition");
    dsl.execute("drop function if exists test_deletion_failure()");
  }

  @Test
  void removesTheOwnedPrivateRowDatesNotesProjectionsAndBothOverrideTables() {
    BookIds book = addBook(FIRST, "Private", "MANUAL", null, null);
    dsl.update(USER_EDITION)
        .set(USER_EDITION.STATE, "FINISHED")
        .set(USER_EDITION.STARTED_ON, LocalDate.parse("2026-01-02"))
        .set(USER_EDITION.FINISHED_ON, LocalDate.parse("2026-02-03"))
        .set(USER_EDITION.PRIVATE_NOTES, "secret")
        .where(USER_EDITION.ID.eq(book.userEditionId()))
        .execute();
    addOverrides(book.userEditionId());

    assertEquals(DeletionStatus.DELETED, deletions.delete(FIRST, book.userEditionId(), 0).status());

    assertEquals(0, dsl.fetchCount(USER_EDITION));
    assertEquals(0, dsl.fetchCount(USER_EDITION_METADATA_OVERRIDE));
    assertEquals(0, dsl.fetchCount(USER_EDITION_AUTHOR_OVERRIDE));
    assertEquals(0, dsl.fetchCount(EDITION));
    assertEquals(0, dsl.fetchCount(EDITION_AUTHOR));
  }

  @Test
  void preservesAnotherOwnersLinkAndPrivateDataUntilTheFinalReferenceIsDeleted() {
    BookIds first = addBook(FIRST, "Shared", "MANUAL", null, null);
    UUID secondId = link(SECOND, first.editionId(), "Shared", "other secret");

    assertEquals(
        DeletionStatus.DELETED, deletions.delete(FIRST, first.userEditionId(), 0).status());
    assertEquals(1, dsl.fetchCount(USER_EDITION));
    assertEquals(
        "other secret",
        dsl.select(USER_EDITION.PRIVATE_NOTES).from(USER_EDITION).fetchOne(0, String.class));
    assertEquals(1, dsl.fetchCount(EDITION));
    assertEquals(DeletionStatus.NOT_FOUND, deletions.delete(FIRST, secondId, 0).status());

    assertEquals(DeletionStatus.DELETED, deletions.delete(SECOND, secondId, 0).status());
    assertEquals(0, dsl.fetchCount(EDITION));
  }

  @Test
  void preservesProviderOriginAndProviderIdentifiedOrphanEditions() {
    BookIds providerOrigin = addBook(FIRST, "Provider origin", "PROVIDER", null, null);
    BookIds providerIdentity =
        addBook(FIRST, "Provider identity", "MANUAL", "catalog", "provider-42");

    assertEquals(
        DeletionStatus.DELETED,
        deletions.delete(FIRST, providerOrigin.userEditionId(), 0).status());
    assertEquals(
        DeletionStatus.DELETED,
        deletions.delete(FIRST, providerIdentity.userEditionId(), 0).status());

    assertEquals(2, dsl.fetchCount(EDITION));
    assertEquals(2, dsl.fetchCount(EDITION_AUTHOR));
  }

  @Test
  void staleAndInaccessibleDeletesHaveDeletionSpecificResultsWithoutMutation() {
    BookIds book = addBook(FIRST, "Optimistic", "MANUAL", null, null);
    dsl.update(USER_EDITION)
        .set(USER_EDITION.VERSION, 1L)
        .where(USER_EDITION.ID.eq(book.userEditionId()))
        .execute();

    assertEquals(
        DeletionStatus.CONFLICT, deletions.delete(FIRST, book.userEditionId(), 0).status());
    assertEquals(
        DeletionStatus.NOT_FOUND, deletions.delete(SECOND, book.userEditionId(), 1).status());
    assertEquals(1, dsl.fetchCount(USER_EDITION));
    assertEquals(1, dsl.fetchCount(EDITION));
  }

  @Test
  void concurrentFinalReferenceDeletesAreSerializedAndCleanTheManualEditionOnce() throws Exception {
    BookIds first = addBook(FIRST, "Concurrent", "MANUAL", null, null);
    UUID secondId = link(SECOND, first.editionId(), "Concurrent", null);
    CyclicBarrier ready = new CyclicBarrier(2);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                ready.await();
                return deletions.delete(FIRST, first.userEditionId(), 0);
              });
      var two =
          executor.submit(
              () -> {
                ready.await();
                return deletions.delete(SECOND, secondId, 0);
              });
      assertEquals(DeletionStatus.DELETED, one.get().status());
      assertEquals(DeletionStatus.DELETED, two.get().status());
    }

    assertEquals(0, dsl.fetchCount(USER_EDITION));
    assertEquals(0, dsl.fetchCount(EDITION));
    assertEquals(0, dsl.fetchCount(EDITION_AUTHOR));
  }

  @Test
  void databaseFailuresBeforeAndAfterPrivateDeleteRollBackTheWholeTransaction() {
    assertRollbackFor("before");
    cleanLibrary();
    assertRollbackFor("after");
  }

  private void assertRollbackFor(String timing) {
    BookIds book = addBook(FIRST, "Retryable", "MANUAL", null, null);
    addOverrides(book.userEditionId());
    dsl.execute(
        "create function test_deletion_failure() returns trigger language plpgsql as $$"
            + " begin raise exception 'planned deletion failure'; end $$");
    dsl.execute(
        "create trigger test_deletion_failure "
            + timing
            + " delete on user_edition for each row execute function test_deletion_failure()");

    assertThrows(RuntimeException.class, () -> deletions.delete(FIRST, book.userEditionId(), 0));
    assertEquals(1, dsl.fetchCount(USER_EDITION));
    assertEquals(1, dsl.fetchCount(USER_EDITION_METADATA_OVERRIDE));
    assertEquals(1, dsl.fetchCount(USER_EDITION_AUTHOR_OVERRIDE));
    assertEquals(1, dsl.fetchCount(EDITION));
    assertEquals(1, dsl.fetchCount(EDITION_AUTHOR));

    dropFailureTrigger();
    assertEquals(DeletionStatus.DELETED, deletions.delete(FIRST, book.userEditionId(), 0).status());
    assertFalse(deletions.find(FIRST, book.userEditionId()).isPresent());
  }

  private BookIds addBook(
      CurrentUser owner, String title, String origin, String providerName, String providerId) {
    UUID editionId = UUID.randomUUID();
    UUID userEditionId = UUID.randomUUID();
    var timestamp = Instant.parse("2026-08-01T10:00:00Z").atOffset(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, editionId)
        .set(EDITION.PROVIDER_NAME, providerName)
        .set(EDITION.PROVIDER_EDITION_ID, providerId)
        .set(EDITION.TITLE, title)
        .set(EDITION.METADATA_ORIGIN, origin)
        .set(EDITION.CREATED_AT, timestamp)
        .set(EDITION.UPDATED_AT, timestamp)
        .execute();
    dsl.insertInto(EDITION_AUTHOR)
        .set(EDITION_AUTHOR.EDITION_ID, editionId)
        .set(EDITION_AUTHOR.POSITION, 0)
        .set(EDITION_AUTHOR.NAME, "Author")
        .execute();
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, userEditionId)
        .set(USER_EDITION.USER_ID, owner.id().value())
        .set(USER_EDITION.EDITION_ID, editionId)
        .set(USER_EDITION.STATE, "TO_READ")
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, title)
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Author")
        .set(USER_EDITION.CREATED_AT, timestamp)
        .set(USER_EDITION.UPDATED_AT, timestamp)
        .set(USER_EDITION.VERSION, 0L)
        .execute();
    return new BookIds(editionId, userEditionId);
  }

  private UUID link(CurrentUser owner, UUID editionId, String title, String notes) {
    UUID id = UUID.randomUUID();
    var timestamp = Instant.parse("2026-08-01T10:00:00Z").atOffset(ZoneOffset.UTC);
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, id)
        .set(USER_EDITION.USER_ID, owner.id().value())
        .set(USER_EDITION.EDITION_ID, editionId)
        .set(USER_EDITION.STATE, "READING")
        .set(USER_EDITION.STARTED_ON, LocalDate.parse("2026-07-01"))
        .set(USER_EDITION.PRIVATE_NOTES, notes)
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, title)
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, "Other Author")
        .set(USER_EDITION.CREATED_AT, timestamp)
        .set(USER_EDITION.UPDATED_AT, timestamp)
        .set(USER_EDITION.VERSION, 0L)
        .execute();
    return id;
  }

  private void addOverrides(UUID userEditionId) {
    dsl.insertInto(USER_EDITION_METADATA_OVERRIDE)
        .set(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID, userEditionId)
        .set(USER_EDITION_METADATA_OVERRIDE.TITLE_IS_OVERRIDDEN, true)
        .set(USER_EDITION_METADATA_OVERRIDE.TITLE_VALUE, "Private title")
        .set(USER_EDITION_METADATA_OVERRIDE.SUBTITLE_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN, true)
        .set(USER_EDITION_METADATA_OVERRIDE.FORMAT_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.ISBN_10_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.ISBN_13_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.PUBLISHER_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.PUBLICATION_DATE_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.PAGE_COUNT_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.LANGUAGE_IS_OVERRIDDEN, false)
        .set(USER_EDITION_METADATA_OVERRIDE.DESCRIPTION_IS_OVERRIDDEN, false)
        .execute();
    dsl.insertInto(USER_EDITION_AUTHOR_OVERRIDE)
        .set(USER_EDITION_AUTHOR_OVERRIDE.USER_EDITION_ID, userEditionId)
        .set(USER_EDITION_AUTHOR_OVERRIDE.POSITION, 0)
        .set(USER_EDITION_AUTHOR_OVERRIDE.NAME, "Private Author")
        .execute();
  }

  private record BookIds(UUID editionId, UUID userEditionId) {}
}
