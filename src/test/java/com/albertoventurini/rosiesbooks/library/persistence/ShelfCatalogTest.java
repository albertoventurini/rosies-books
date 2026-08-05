package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfBook;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfCatalog;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ShelfCatalogTest {

  private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

  @Inject DSLContext dsl;
  @Inject ShelfCatalog shelves;

  private CurrentUser firstUser;
  private CurrentUser secondUser;

  @BeforeEach
  void cleanLibrary() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, edition restart identity cascade");
    firstUser = DevelopmentUser.READER_ONE.currentUser();
    secondUser = DevelopmentUser.READER_TWO.currentUser();
  }

  @Test
  void filtersByExactStateAndOwnerAndResolvesPrivateTitleAndOrderedAuthors() {
    UUID reading = addBook(firstUser, "READING", "Canonical Reading", List.of("B", "A"), 1);
    addBook(firstUser, "TO_READ", "To Read", List.of("To Read Author"), 2);
    addBook(firstUser, "FINISHED", "Finished", List.of("Finished Author"), 3);
    addBook(secondUser, "READING", "Other user's private title", List.of("Other Author"), 4);
    overrideMetadata(reading, "My Private Title", List.of("Second", "First"));

    assertEquals(
        List.of(
            new ShelfBook(
                new com.albertoventurini.rosiesbooks.library.internal.UserEditionId(reading),
                "My Private Title",
                List.of("Second", "First"),
                new com.albertoventurini.rosiesbooks.library.internal.Reading(
                    LocalDate.of(2026, 1, 2)),
                BASE_TIME.plusSeconds(1))),
        shelves.find(firstUser, Shelf.READING));
    UUID otherId = new UUID(2, 4);
    assertEquals(
        List.of(
            new ShelfBook(
                new com.albertoventurini.rosiesbooks.library.internal.UserEditionId(otherId),
                "Other user's private title",
                List.of("Other Author"),
                new com.albertoventurini.rosiesbooks.library.internal.Reading(
                    LocalDate.of(2026, 1, 2)),
                BASE_TIME.plusSeconds(1))),
        shelves.find(secondUser, Shelf.READING));
  }

  @Test
  void appliesEachDefaultOrderWithAscendingUuidTieBreaks() {
    addBook(firstUser, "READING", "Older reading", List.of("A"), 11);
    addBook(firstUser, "READING", "Tie reading second", List.of("B"), 13);
    addBook(firstUser, "READING", "Tie reading first", List.of("C"), 12);

    addBook(firstUser, "TO_READ", "Older to read", List.of("A"), 21);
    addBook(firstUser, "TO_READ", "Tie to read second", List.of("B"), 23);
    addBook(firstUser, "TO_READ", "Tie to read first", List.of("C"), 22);

    addBook(firstUser, "FINISHED", "Older finished", List.of("A"), 31);
    addBook(firstUser, "FINISHED", "Tie finished second", List.of("B"), 33);
    addBook(firstUser, "FINISHED", "Tie finished first", List.of("C"), 32);

    assertTitles(Shelf.READING, "Tie reading first", "Tie reading second", "Older reading");
    assertTitles(Shelf.TO_READ, "Tie to read first", "Tie to read second", "Older to read");
    assertTitles(Shelf.FINISHED, "Tie finished first", "Tie finished second", "Older finished");
  }

  @Test
  void projectsEachValidStateAndTheUserEditionCreationInstant() {
    UUID reading = addBook(firstUser, "READING", "Reading projection", List.of("A"), 51);
    UUID toRead = addBook(firstUser, "TO_READ", "To Read projection", List.of("B"), 52);
    UUID finished = addBook(firstUser, "FINISHED", "Finished projection", List.of("C"), 53);

    assertEquals(
        new ShelfBook(
            new UserEditionId(reading),
            "Reading projection",
            List.of("A"),
            new Reading(LocalDate.of(2026, 1, 2)),
            BASE_TIME.plusSeconds(1)),
        shelves.find(firstUser, Shelf.READING).getFirst());
    assertEquals(
        new ShelfBook(
            new UserEditionId(toRead),
            "To Read projection",
            List.of("B"),
            new ToRead(),
            BASE_TIME.plusSeconds(1)),
        shelves.find(firstUser, Shelf.TO_READ).getFirst());
    assertEquals(
        new ShelfBook(
            new UserEditionId(finished),
            "Finished projection",
            List.of("C"),
            new Finished(Optional.empty(), LocalDate.of(2026, 1, 2)),
            BASE_TIME.plusSeconds(1)),
        shelves.find(firstUser, Shelf.FINISHED).getFirst());
  }

  @Test
  void returnsEmptyShelvesAndAllFinishedYears() {
    assertEquals(List.of(), shelves.find(firstUser, Shelf.READING));
    addBook(firstUser, "FINISHED", "Finished in 2026", List.of("A"), 41);
    addBook(firstUser, "FINISHED", "Finished in 2024", List.of("B"), 42);

    assertTitles(Shelf.FINISHED, "Finished in 2026", "Finished in 2024");
  }

  private void assertTitles(Shelf shelf, String... expected) {
    assertEquals(
        List.of(expected), shelves.find(firstUser, shelf).stream().map(ShelfBook::title).toList());
  }

  private UUID addBook(
      CurrentUser owner, String state, String title, List<String> authors, int sequence) {
    UUID editionId = new UUID(1, sequence);
    UUID userEditionId = new UUID(2, sequence);
    OffsetDateTime timestamp =
        BASE_TIME.plusSeconds(title.startsWith("Older") ? 0 : 1).atOffset(ZoneOffset.UTC);
    dsl.insertInto(EDITION)
        .set(EDITION.ID, editionId)
        .set(EDITION.TITLE, title)
        .set(EDITION.METADATA_ORIGIN, "MANUAL")
        .set(EDITION.CREATED_AT, timestamp)
        .set(EDITION.UPDATED_AT, timestamp)
        .execute();
    for (int position = 0; position < authors.size(); position++) {
      dsl.insertInto(EDITION_AUTHOR)
          .set(EDITION_AUTHOR.EDITION_ID, editionId)
          .set(EDITION_AUTHOR.POSITION, position)
          .set(EDITION_AUTHOR.NAME, authors.get(position))
          .execute();
    }
    LocalDate date = LocalDate.of(2026, 1, title.startsWith("Older") ? 1 : 2);
    if (state.equals("FINISHED") && title.endsWith("2024")) {
      date = LocalDate.of(2024, 6, 1);
    }
    dsl.insertInto(USER_EDITION)
        .set(USER_EDITION.ID, userEditionId)
        .set(USER_EDITION.USER_ID, owner.id().value())
        .set(USER_EDITION.EDITION_ID, editionId)
        .set(USER_EDITION.STATE, state)
        .set(USER_EDITION.STARTED_ON, state.equals("READING") ? date : null)
        .set(USER_EDITION.FINISHED_ON, state.equals("FINISHED") ? date : null)
        .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, title)
        .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, String.join(" ", authors))
        .set(USER_EDITION.CREATED_AT, timestamp)
        .set(USER_EDITION.UPDATED_AT, timestamp)
        .execute();
    return userEditionId;
  }

  private void overrideMetadata(UUID userEditionId, String title, List<String> authors) {
    dsl.execute(
        """
        insert into user_edition_metadata_override (
          user_edition_id, title_is_overridden, title_value,
          subtitle_is_overridden, authors_is_overridden, format_is_overridden,
          isbn_10_is_overridden, isbn_13_is_overridden, publisher_is_overridden,
          publication_date_is_overridden, page_count_is_overridden,
          language_is_overridden, description_is_overridden)
        values (?, true, ?, false, true, false, false, false, false, false, false, false, false)
        """,
        userEditionId,
        title);
    for (int position = 0; position < authors.size(); position++) {
      dsl.execute(
          """
          insert into user_edition_author_override
            (user_edition_id, authors_is_overridden, position, name)
          values (?, true, ?, ?)
          """,
          userEditionId,
          position,
          authors.get(position));
    }
  }
}
