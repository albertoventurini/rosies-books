package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ManualBookAdditionServiceTest {

  private static final CurrentUser FIRST = DevelopmentUser.READER_ONE.currentUser();
  private static final CurrentUser SECOND = DevelopmentUser.READER_TWO.currentUser();

  @Inject ManualBookAdditionService additions;
  @Inject DSLContext dsl;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
  }

  @Test
  void createsIdentifierlessAndIsbnEditionsWithOrderedAuthorsOptionalMetadataAndEveryState() {
    EditionMetadata complete =
        new EditionMetadata(
            "Complete",
            Optional.of("Subtitle"),
            List.of("Second-listed", "First-listed"),
            Optional.of("Hardback"),
            Optional.of(Isbn10.parse("0-306-40615-2")),
            Optional.of(Isbn13.parse("9780306406157")),
            Optional.of("Publisher"),
            Optional.of(PartialPublicationDate.full(2024, 2, 29)),
            Optional.of(321),
            Optional.of("en"),
            Optional.of("Description"));
    additions.add(FIRST, UUID.randomUUID(), complete, new ToRead());

    var edition = dsl.selectFrom(EDITION).fetchSingle();
    assertEquals("0306406152", edition.get(EDITION.ISBN_10));
    assertEquals("9780306406157", edition.get(EDITION.ISBN_13));
    assertEquals("MANUAL", edition.get(EDITION.METADATA_ORIGIN));
    assertNull(edition.get(EDITION.PROVIDER_NAME));
    assertNull(edition.get(EDITION.PROVIDER_EDITION_ID));
    assertNull(edition.get(EDITION.COVER_ASSET_ID));
    assertEquals("Subtitle", edition.get(EDITION.SUBTITLE));
    assertEquals(Integer.valueOf(2024), edition.get(EDITION.PUBLICATION_YEAR));
    assertEquals(Integer.valueOf(2), edition.get(EDITION.PUBLICATION_MONTH));
    assertEquals(Integer.valueOf(29), edition.get(EDITION.PUBLICATION_DAY));
    assertEquals(Integer.valueOf(321), edition.get(EDITION.PAGE_COUNT));
    assertEquals(
        List.of("Second-listed", "First-listed"),
        dsl.select(EDITION_AUTHOR.NAME)
            .from(EDITION_AUTHOR)
            .orderBy(EDITION_AUTHOR.POSITION)
            .fetch(EDITION_AUTHOR.NAME));

    List<ReadingState> states =
        List.of(
            new ToRead(),
            new Reading(LocalDate.parse("2026-08-01")),
            new Finished(Optional.of(LocalDate.parse("2026-07-01")), LocalDate.parse("2026-08-02")),
            new Finished(Optional.empty(), LocalDate.parse("2026-08-03")));
    for (int index = 0; index < states.size(); index++) {
      additions.add(
          FIRST,
          UUID.randomUUID(),
          minimal("Identifierless " + index, Optional.empty()),
          states.get(index));
    }
    assertEquals(5, dsl.fetchCount(EDITION));
    assertEquals(5, dsl.fetchCount(USER_EDITION));
    assertEquals(
        List.of("FINISHED", "FINISHED", "READING", "TO_READ", "TO_READ"),
        dsl.select(USER_EDITION.STATE)
            .from(USER_EDITION)
            .orderBy(USER_EDITION.STATE, USER_EDITION.FINISHED_ON)
            .fetch(USER_EDITION.STATE));
    assertEquals(
        4, dsl.fetchCount(EDITION, EDITION.ISBN_13.isNull().and(EDITION.PROVIDER_NAME.isNull())));
    assertStateDates("Identifierless 0", "TO_READ", null, null);
    assertStateDates("Identifierless 1", "READING", "2026-08-01", null);
    assertStateDates("Identifierless 2", "FINISHED", "2026-07-01", "2026-08-02");
    assertStateDates("Identifierless 3", "FINISHED", null, "2026-08-03");
  }

  @Test
  void canonicalIsbnResolutionReusesSharedMetadataWithoutOverwritingItForEitherOwner() {
    EditionMetadata original =
        new EditionMetadata(
            "Canonical title",
            Optional.of("Canonical subtitle"),
            List.of("Canonical author"),
            Optional.empty(),
            Optional.of(Isbn10.parse("0-306-40615-2")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    UUID ownerScopedRequest = UUID.randomUUID();
    var first = additions.add(FIRST, ownerScopedRequest, original, new ToRead());
    EditionMetadata conflicting =
        minimal("Do not overwrite", Optional.of(Isbn13.parse("978-0-306-40615-7")));
    var second =
        additions.add(SECOND, ownerScopedRequest, conflicting, new Reading(LocalDate.now()));

    assertEquals(1, dsl.fetchCount(EDITION));
    assertEquals(2, dsl.fetchCount(USER_EDITION));
    assertEquals("Canonical title", dsl.fetchSingle(EDITION).get(EDITION.TITLE));
    assertEquals("Canonical subtitle", dsl.fetchSingle(EDITION).get(EDITION.SUBTITLE));
    assertEquals(
        List.of("Canonical author"),
        dsl.select(EDITION_AUTHOR.NAME).from(EDITION_AUTHOR).fetch(EDITION_AUTHOR.NAME));
    assertTrue(!first.id().equals(second.id()));
  }

  @Test
  void anExistingOwnerLinkIsReturnedWithoutChangingPrivateOrStateData() {
    EditionMetadata metadata = minimal("Original", Optional.of(Isbn13.parse("9780306406157")));
    UUID originalRequest = UUID.randomUUID();
    var original = additions.add(FIRST, originalRequest, metadata, new ToRead());
    OffsetDateTime privateTimestamp = OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
    dsl.update(USER_EDITION)
        .set(USER_EDITION.PRIVATE_NOTES, "private")
        .set(USER_EDITION.UPDATED_AT, privateTimestamp)
        .where(USER_EDITION.ID.eq(original.id().value()))
        .execute();

    var repeated =
        additions.add(
            FIRST,
            UUID.randomUUID(),
            minimal("Changed", Optional.of(Isbn13.parse("9780306406157"))),
            new Finished(Optional.empty(), LocalDate.parse("2026-08-04")));

    assertEquals(original.id(), repeated.id());
    var row = dsl.selectFrom(USER_EDITION).fetchSingle();
    assertEquals("TO_READ", row.get(USER_EDITION.STATE));
    assertNull(row.get(USER_EDITION.STARTED_ON));
    assertNull(row.get(USER_EDITION.FINISHED_ON));
    assertEquals("private", row.get(USER_EDITION.PRIVATE_NOTES));
    assertEquals(privateTimestamp.toInstant(), row.get(USER_EDITION.UPDATED_AT).toInstant());
    assertEquals(originalRequest, row.get(USER_EDITION.REQUEST_ID));
  }

  @Test
  void exactIdentifierlessRetryReturnsTheOriginalAdditionWithoutFuzzyMergingOtherRequests() {
    UUID request = UUID.randomUUID();
    var first =
        additions.add(FIRST, request, minimal("Same title", Optional.empty()), new ToRead());
    var retry =
        additions.add(
            FIRST,
            request,
            minimal("Changed retry", Optional.empty()),
            new Finished(Optional.empty(), LocalDate.parse("2026-08-04")));
    additions.add(FIRST, UUID.randomUUID(), minimal("Same title", Optional.empty()), new ToRead());

    assertEquals(first, retry);
    assertEquals(2, dsl.fetchCount(EDITION));
    assertEquals(2, dsl.fetchCount(USER_EDITION));
    assertEquals(
        List.of("Same title", "Same title"),
        dsl.select(EDITION.TITLE)
            .from(EDITION)
            .orderBy(EDITION.CREATED_AT, EDITION.ID)
            .fetch(EDITION.TITLE));
  }

  @Test
  void coordinatedConcurrentRetriesAndIsbnSubmissionsConvergeOnOneEditionAndOneOwnerLink()
      throws Exception {
    UUID exactRequest = UUID.randomUUID();
    var exact =
        runTogether(
            () ->
                additions.add(
                    FIRST, exactRequest, minimal("Concurrent A", Optional.empty()), new ToRead()),
            () ->
                additions.add(
                    FIRST, exactRequest, minimal("Concurrent B", Optional.empty()), new ToRead()));
    assertEquals(exact.get(0).id(), exact.get(1).id());
    assertEquals(1, dsl.fetchCount(EDITION));
    assertEquals(1, dsl.fetchCount(USER_EDITION));

    cleanLibrary();
    Optional<Isbn13> isbn = Optional.of(Isbn13.parse("9780306406157"));
    var canonical =
        runTogether(
            () -> additions.add(FIRST, UUID.randomUUID(), minimal("ISBN A", isbn), new ToRead()),
            () -> additions.add(FIRST, UUID.randomUUID(), minimal("ISBN B", isbn), new ToRead()));
    assertEquals(canonical.get(0).id(), canonical.get(1).id());
    assertEquals(1, dsl.fetchCount(EDITION));
    assertEquals(1, dsl.fetchCount(USER_EDITION));
  }

  @Test
  void failuresAtEditionAuthorAndUserEditionBoundariesLeaveNoPartialRows() {
    assertAtomicFailure(
        "alter table edition add constraint test_manual_edition_failure"
            + " check (title <> 'FAIL_EDITION')",
        "alter table edition drop constraint test_manual_edition_failure",
        minimal("FAIL_EDITION", Optional.empty()));
    assertAtomicFailure(
        "alter table edition_author add constraint test_manual_author_failure"
            + " check (name <> 'Author')",
        "alter table edition_author drop constraint test_manual_author_failure",
        minimal("FAIL_AUTHOR", Optional.empty()));
    assertAtomicFailure(
        "alter table user_edition add constraint test_manual_link_failure"
            + " check (effective_title_search <> 'FAIL_LINK')",
        "alter table user_edition drop constraint test_manual_link_failure",
        minimal("FAIL_LINK", Optional.empty()));
  }

  private void assertAtomicFailure(
      String addConstraint, String dropConstraint, EditionMetadata metadata) {
    dsl.execute(addConstraint);
    try {
      assertThrows(
          RuntimeException.class,
          () -> additions.add(FIRST, UUID.randomUUID(), metadata, new ToRead()));
      assertEquals(0, dsl.fetchCount(EDITION));
      assertEquals(0, dsl.fetchCount(EDITION_AUTHOR));
      assertEquals(0, dsl.fetchCount(USER_EDITION));
    } finally {
      dsl.execute(dropConstraint);
    }
  }

  private void assertStateDates(
      String title, String state, String expectedStartedOn, String expectedFinishedOn) {
    var row =
        dsl.select(USER_EDITION.STATE, USER_EDITION.STARTED_ON, USER_EDITION.FINISHED_ON)
            .from(USER_EDITION)
            .join(EDITION)
            .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
            .where(EDITION.TITLE.eq(title))
            .fetchSingle();
    assertEquals(state, row.get(USER_EDITION.STATE));
    assertEquals(
        expectedStartedOn == null ? null : LocalDate.parse(expectedStartedOn),
        row.get(USER_EDITION.STARTED_ON));
    assertEquals(
        expectedFinishedOn == null ? null : LocalDate.parse(expectedFinishedOn),
        row.get(USER_EDITION.FINISHED_ON));
  }

  private static EditionMetadata minimal(String title, Optional<Isbn13> isbn13) {
    return new EditionMetadata(
        title,
        Optional.empty(),
        List.of("Author"),
        Optional.empty(),
        Optional.empty(),
        isbn13,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static List<ManualBookAdditionService.AddedBook> runTogether(
      java.util.concurrent.Callable<ManualBookAdditionService.AddedBook> first,
      java.util.concurrent.Callable<ManualBookAdditionService.AddedBook> second)
      throws Exception {
    CyclicBarrier ready = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<ManualBookAdditionService.AddedBook> firstResult =
          executor.submit(
              () -> {
                ready.await();
                return first.call();
              });
      Future<ManualBookAdditionService.AddedBook> secondResult =
          executor.submit(
              () -> {
                ready.await();
                return second.call();
              });
      return List.of(firstResult.get(), secondResult.get());
    }
  }
}
