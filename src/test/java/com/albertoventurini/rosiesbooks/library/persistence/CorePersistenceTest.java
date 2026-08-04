package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import com.albertoventurini.rosiesbooks.library.internal.CanonicalIsbns;
import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn10;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverride;
import com.albertoventurini.rosiesbooks.library.internal.MetadataOverrides;
import com.albertoventurini.rosiesbooks.library.internal.MoveToFinished;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ReadingStateTransitions;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
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
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CorePersistenceTest {

  private static final Instant CREATED = Instant.parse("2026-08-03T10:15:30Z");
  private static final Instant UPDATED = Instant.parse("2026-08-03T11:16:31Z");

  @Inject DSLContext dsl;
  @Inject CoverAssetRepository covers;
  @Inject UserPreferenceRepository preferences;
  @Inject EditionRepository editions;
  @Inject UserEditionRepository userEditions;
  @Inject MetadataOverrideRepository overrides;
  @Inject CorePersistenceTestCoordinator coordinator;

  private UserId firstUser;
  private UserId secondUser;

  @BeforeEach
  void createUsers() {
    firstUser = new UserId(UUID.randomUUID());
    secondUser = new UserId(UUID.randomUUID());
    insertUser(firstUser, "first");
    insertUser(secondUser, "second");
  }

  @AfterEach
  void removeRows() {
    dsl.execute(
        "truncate table user_edition_author_override, user_edition_metadata_override,"
            + " user_edition, edition_author, user_preference, edition, app_user, cover_asset"
            + " restart identity cascade");
  }

  @Test
  void roundTripsEveryCanonicalFieldOrderedAuthorsAndAllDatePrecisions() {
    UUID coverId = UUID.randomUUID();
    covers.save(coverId, new byte[] {1, 2, 3}, "image/png");
    Edition full =
        edition(
            "9780306406157",
            "provider",
            "Case-Sensitive-ID",
            PartialPublicationDate.full(2024, 2, 29),
            coverId);
    coordinator.createEdition(full);

    assertEquals(full, editions.find(full.id()).orElseThrow());

    for (PartialPublicationDate date :
        List.of(
            PartialPublicationDate.unknown(),
            PartialPublicationDate.year(1984),
            PartialPublicationDate.yearMonth(1984, 6))) {
      Edition value = edition(null, null, null, date, null);
      coordinator.createEdition(value);
      assertEquals(date, editions.find(value.id()).orElseThrow().publicationDate());
    }
  }

  @Test
  void derivesPersistsAndLooksUpCanonicalIsbn13FromIsbn10() {
    Isbn10 supplied = Isbn10.parse(" 0-306-40615-2 ");
    Edition edition =
        editionWithIsbns(
            new CanonicalIsbns(Optional.of(supplied), Optional.empty()),
            null,
            null,
            PartialPublicationDate.unknown(),
            null);

    coordinator.createEdition(edition);

    var stored =
        dsl.select(EDITION.ISBN_10, EDITION.ISBN_13)
            .from(EDITION)
            .where(EDITION.ID.eq(edition.id().value()))
            .fetchSingle();
    assertEquals("0306406152", stored.value1());
    assertEquals("9780306406157", stored.value2());
    assertEquals(edition.id(), editions.findByIsbn(supplied).orElseThrow().id());
    assertEquals(
        edition.id(), editions.findByIsbn(Isbn13.parse("978-0-306-40615-7")).orElseThrow().id());

    UserEdition linked = userEdition(edition.id());
    coordinator.link(firstUser, linked);
    MetadataOverrides privateIsbn =
        new MetadataOverrides(
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.value(Isbn13.parse("9791090636071")),
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.inherited(),
            MetadataOverride.inherited());
    assertTrue(coordinator.saveOverrides(firstUser, linked.id(), privateIsbn));
    assertTrue(editions.findByIsbn(Isbn13.parse("9791090636071")).isEmpty());
    assertEquals(edition.id(), editions.findByIsbn(supplied).orElseThrow().id());
  }

  @Test
  void persistsPreferencesLinksCanonicalSearchDataAndPrivateOverrides() {
    preferences.save(firstUser, LibraryLayout.COMPACT_LIST);
    assertEquals(LibraryLayout.COMPACT_LIST, preferences.find(firstUser).orElseThrow());

    Edition edition = edition(null, null, null, PartialPublicationDate.year(1999), null);
    coordinator.createEdition(edition);
    UserEdition linked = userEdition(edition.id());
    coordinator.link(firstUser, linked);
    assertEquals(linked, userEditions.find(firstUser, linked.id()).orElseThrow());
    var projections =
        dsl.select(USER_EDITION.EFFECTIVE_TITLE_SEARCH, USER_EDITION.EFFECTIVE_AUTHORS_SEARCH)
            .from(USER_EDITION)
            .where(USER_EDITION.ID.eq(linked.id().value()))
            .fetchSingle();
    assertEquals(edition.title(), projections.value1());
    assertEquals(String.join(" ", edition.authors()), projections.value2());

    MetadataOverrides metadataOverrides =
        new MetadataOverrides(
            MetadataOverride.value("Private title"),
            MetadataOverride.blank(),
            MetadataOverride.value(List.of("Second Author", "First Author")),
            MetadataOverride.inherited(),
            MetadataOverride.value(Isbn10.parse("080442957X")),
            MetadataOverride.blank(),
            MetadataOverride.value("Private publisher"),
            MetadataOverride.value(PartialPublicationDate.yearMonth(2020, 7)),
            MetadataOverride.value(321),
            MetadataOverride.inherited(),
            MetadataOverride.blank());
    assertTrue(coordinator.saveOverrides(firstUser, linked.id(), metadataOverrides));
    assertEquals(metadataOverrides, overrides.find(firstUser, linked.id()).orElseThrow());
    projections =
        dsl.select(USER_EDITION.EFFECTIVE_TITLE_SEARCH, USER_EDITION.EFFECTIVE_AUTHORS_SEARCH)
            .from(USER_EDITION)
            .where(USER_EDITION.ID.eq(linked.id().value()))
            .fetchSingle();
    assertEquals("Private title", projections.value1());
    assertEquals("Second Author First Author", projections.value2());

    assertTrue(coordinator.saveOverrides(firstUser, linked.id(), inheritedOverrides()));
    projections =
        dsl.select(USER_EDITION.EFFECTIVE_TITLE_SEARCH, USER_EDITION.EFFECTIVE_AUTHORS_SEARCH)
            .from(USER_EDITION)
            .where(USER_EDITION.ID.eq(linked.id().value()))
            .fetchSingle();
    assertEquals(edition.title(), projections.value1());
    assertEquals(String.join(" ", edition.authors()), projections.value2());
  }

  @Test
  void roundTripsEveryValidReadingStateShape() {
    List<ReadingState> states =
        List.of(
            new ToRead(),
            new Reading(LocalDate.of(2026, 8, 1)),
            new Finished(Optional.empty(), LocalDate.of(2026, 8, 3)),
            new Finished(Optional.of(LocalDate.of(2026, 7, 1)), LocalDate.of(2026, 8, 3)));

    for (ReadingState state : states) {
      Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
      coordinator.createEdition(edition);
      UserEdition linked = userEdition(edition.id(), state);

      coordinator.link(firstUser, linked);

      assertEquals(linked, userEditions.find(firstUser, linked.id()).orElseThrow());
    }
  }

  @Test
  void updatesReadingStateAndTimestampOnlyForTheOwningUserAndKnownRecord() {
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition original = userEdition(edition.id(), new ToRead());
    coordinator.link(firstUser, original);
    Instant stateUpdatedAt = Instant.parse("2026-08-04T12:34:56Z");

    assertFalse(
        userEditions.updateState(
            secondUser, original.id(), new Reading(LocalDate.of(2026, 8, 4)), stateUpdatedAt));
    assertFalse(
        userEditions.updateState(
            firstUser,
            new UserEditionId(UUID.randomUUID()),
            new Reading(LocalDate.of(2026, 8, 4)),
            stateUpdatedAt));
    assertEquals(original, userEditions.find(firstUser, original.id()).orElseThrow());

    assertTrue(
        coordinator.updateState(
            firstUser, original.id(), new Reading(LocalDate.of(2026, 8, 4)), stateUpdatedAt));

    UserEdition updated = userEditions.find(firstUser, original.id()).orElseThrow();
    assertEquals(original.id(), updated.id());
    assertEquals(original.editionId(), updated.editionId());
    assertEquals(original.privateNotes(), updated.privateNotes());
    assertEquals(original.createdAt(), updated.createdAt());
    assertEquals(new Reading(LocalDate.of(2026, 8, 4)), updated.state());
    assertEquals(stateUpdatedAt, updated.updatedAt());
  }

  @Test
  void rejectsEveryInvalidPersistedReadingStateShapeAndReversedDates() {
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition linked = userEdition(edition.id(), new ToRead());
    coordinator.link(firstUser, linked);
    LocalDate start = LocalDate.of(2026, 8, 2);
    LocalDate finish = LocalDate.of(2026, 8, 3);

    assertInvalidPersistedState(linked.id(), "TO_READ", start, null, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "TO_READ", null, finish, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "TO_READ", start, finish, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "READING", null, null, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "READING", null, finish, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "READING", start, finish, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "FINISHED", null, null, "user_edition_state_dates");
    assertInvalidPersistedState(linked.id(), "FINISHED", start, null, "user_edition_state_dates");
    assertInvalidPersistedState(
        linked.id(), "FINISHED", finish.plusDays(1), finish, "user_edition_date_chronology");
    assertEquals(linked, userEditions.find(firstUser, linked.id()).orElseThrow());
  }

  @Test
  void rollsBackAStateUpdateWhenItsTransactionLaterFails() {
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition original = userEdition(edition.id(), new ToRead());
    coordinator.link(firstUser, original);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReadingStateTransitions()
                .plan(
                    original.state(),
                    new MoveToFinished(
                        LocalDate.of(2026, 8, 4), Optional.of(LocalDate.of(2026, 8, 5)))));
    assertEquals(original, userEditions.find(firstUser, original.id()).orElseThrow());

    assertThrows(
        CorePersistenceTestCoordinator.DeliberateFailure.class,
        () ->
            coordinator.updateStateThenFail(
                firstUser,
                original.id(),
                new Finished(Optional.empty(), LocalDate.of(2026, 8, 4)),
                Instant.parse("2026-08-04T12:34:56Z")));

    assertEquals(original, userEditions.find(firstUser, original.id()).orElseThrow());
  }

  @Test
  void keepsEveryUserEditionAndOverrideOperationOwnerScoped() {
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition linked = userEdition(edition.id());
    coordinator.link(firstUser, linked);
    MetadataOverrides original = inheritedOverrides();
    assertTrue(coordinator.saveOverrides(firstUser, linked.id(), original));

    assertTrue(userEditions.find(secondUser, linked.id()).isEmpty());
    assertTrue(overrides.find(secondUser, linked.id()).isEmpty());
    assertFalse(
        coordinator.saveOverrides(
            secondUser,
            linked.id(),
            new MetadataOverrides(
                MetadataOverride.value("stolen"),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited(),
                MetadataOverride.inherited())));
    assertEquals(original, overrides.find(firstUser, linked.id()).orElseThrow());
    assertFalse(userEditions.delete(secondUser, linked.id()));
    assertTrue(userEditions.find(firstUser, linked.id()).isPresent());
    assertFalse(
        coordinator.saveOverrides(
            firstUser, new UserEditionId(UUID.randomUUID()), inheritedOverrides()));
  }

  @Test
  void roundTripsExplicitBlankScalarsDatesNumbersAndAuthors() {
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition linked = userEdition(edition.id());
    coordinator.link(firstUser, linked);
    MetadataOverrides blank =
        new MetadataOverrides(
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank(),
            MetadataOverride.blank());

    assertTrue(coordinator.saveOverridesDirect(firstUser, linked.id(), blank));
    assertEquals(blank, overrides.find(firstUser, linked.id()).orElseThrow());
  }

  @Test
  void invalidMetadataAndLaterFailuresRollBackOverridesAndSearchProjections() {
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition linked = userEdition(edition.id());
    coordinator.link(firstUser, linked);
    MetadataOverrides original =
        inheritedOverrides().withTitle(MetadataOverride.value("Original private title"));
    assertTrue(coordinator.saveOverrides(firstUser, linked.id(), original));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            coordinator.saveOverrides(
                firstUser, linked.id(), inheritedOverrides().withTitle(MetadataOverride.blank())));
    assertEquals(original, overrides.find(firstUser, linked.id()).orElseThrow());
    assertEquals("Original private title", effectiveTitle(linked.id()));

    MetadataOverrides replacement =
        inheritedOverrides().withTitle(MetadataOverride.value("Rolled back title"));
    assertThrows(
        CorePersistenceTestCoordinator.DeliberateFailure.class,
        () -> coordinator.saveOverridesThenFail(firstUser, linked.id(), replacement));
    assertEquals(original, overrides.find(firstUser, linked.id()).orElseThrow());
    assertEquals("Original private title", effectiveTitle(linked.id()));

    dsl.execute(
        "alter table user_edition add constraint test_projection_failure"
            + " check (effective_title_search <> 'Blocked projection')");
    try {
      MetadataOverrides blocked =
          inheritedOverrides().withTitle(MetadataOverride.value("Blocked projection"));
      assertThrows(
          DataAccessException.class,
          () -> coordinator.saveOverrides(firstUser, linked.id(), blocked));
      assertEquals(original, overrides.find(firstUser, linked.id()).orElseThrow());
      assertEquals("Original private title", effectiveTitle(linked.id()));
    } finally {
      dsl.execute("alter table user_edition drop constraint test_projection_failure");
    }
  }

  @Test
  void mapsIdentifierAndPerUserLinkConflictsWhileAllowingTwoOwners() {
    Edition original =
        edition("9780306406157", "catalog", "Mixed-Case-1", PartialPublicationDate.unknown(), null);
    coordinator.createEdition(original);
    assertThrows(
        DuplicateIsbn13Exception.class,
        () ->
            coordinator.createEdition(
                edition(
                    original.isbns().isbn13().orElseThrow().value(),
                    null,
                    null,
                    PartialPublicationDate.unknown(),
                    null)));
    assertThrows(
        DuplicateProviderEditionException.class,
        () ->
            coordinator.createEdition(
                edition(
                    null,
                    original.providerName(),
                    original.providerEditionId(),
                    PartialPublicationDate.unknown(),
                    null)));

    coordinator.link(firstUser, userEdition(original.id()));
    assertThrows(
        EditionAlreadyLinkedException.class,
        () -> coordinator.link(firstUser, userEdition(original.id())));
    UserEdition secondLink = userEdition(original.id());
    coordinator.link(secondUser, secondLink);
    assertTrue(userEditions.find(secondUser, secondLink.id()).isPresent());
  }

  @Test
  void enforcesDeletionOwnershipAndSharedCanonicalLifetimes() {
    UUID coverId = UUID.randomUUID();
    covers.save(coverId, new byte[] {9}, "image/jpeg");
    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), coverId);
    coordinator.createEdition(edition);
    UserEdition firstLink = userEdition(edition.id());
    UserEdition secondLink = userEdition(edition.id());
    coordinator.link(firstUser, firstLink);
    coordinator.link(secondUser, secondLink);
    preferences.save(firstUser, LibraryLayout.COVER_CARD);
    coordinator.saveOverrides(firstUser, firstLink.id(), inheritedOverrides());

    assertThrows(DataAccessException.class, () -> editions.delete(edition.id()));
    dsl.deleteFrom(com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER)
        .where(
            com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER.ID.eq(
                firstUser.value()))
        .execute();
    assertTrue(userEditions.find(firstUser, firstLink.id()).isEmpty());
    assertTrue(overrides.find(firstUser, firstLink.id()).isEmpty());
    assertTrue(preferences.find(firstUser).isEmpty());
    assertTrue(userEditions.find(secondUser, secondLink.id()).isPresent());
    assertTrue(editions.find(edition.id()).isPresent());

    assertTrue(covers.delete(coverId));
    assertEquals(null, editions.find(edition.id()).orElseThrow().coverAssetId());
    assertTrue(userEditions.delete(secondUser, secondLink.id()));
    assertTrue(editions.delete(edition.id()));
  }

  @Test
  void rejectsInvalidDirectPublicationComponentsAndRollsBackAggregateWrites() {
    assertInvalidPublicationDate(2026, 13, null);
    assertInvalidPublicationDate(2026, 2, 29);
    assertInvalidPublicationDate(null, 2, null);
    assertInvalidPublicationDate(2026, null, 1);
    assertInvalidPublicationDate(0, null, null);

    Edition rolledBack = edition(null, null, null, PartialPublicationDate.year(2026), null);
    assertThrows(
        CorePersistenceTestCoordinator.DeliberateFailure.class,
        () -> coordinator.createThenFail(rolledBack));
    assertTrue(editions.find(rolledBack.id()).isEmpty());
  }

  @Test
  void rejectsDirectInvalidCanonicalAndOverrideIsbnChecksumsAndConflictingPairs() {
    assertInvalidCanonicalIsbns("0306406153", null, "edition_isbn_10_checksum");
    assertInvalidCanonicalIsbns(null, "9780306406158", "edition_isbn_13_checksum");
    assertInvalidCanonicalIsbns("0306406152", "9780804429573", "edition_isbn_pair_consistent");

    Edition edition = edition(null, null, null, PartialPublicationDate.unknown(), null);
    coordinator.createEdition(edition);
    UserEdition linked = userEdition(edition.id());
    coordinator.link(firstUser, linked);
    DataAccessException invalidOverride =
        assertThrows(
            DataAccessException.class,
            () ->
                dsl.execute(
                    """
                    insert into user_edition_metadata_override (
                      user_edition_id, title_is_overridden, subtitle_is_overridden,
                      authors_is_overridden, format_is_overridden,
                      isbn_10_is_overridden, isbn_10_value, isbn_13_is_overridden,
                      publisher_is_overridden, publication_date_is_overridden,
                      page_count_is_overridden, language_is_overridden,
                      description_is_overridden)
                    values (?, false, false, false, false, true, '0306406153', false,
                            false, false, false, false, false)
                    """,
                    linked.id().value()));
    assertTrue(
        PostgresConstraint.isCheckViolation(
            invalidOverride, "user_edition_metadata_override_isbn_10_checksum"));

    DataAccessException invalidOverride13 =
        assertThrows(
            DataAccessException.class,
            () ->
                dsl.execute(
                    """
                    insert into user_edition_metadata_override (
                      user_edition_id, title_is_overridden, subtitle_is_overridden,
                      authors_is_overridden, format_is_overridden,
                      isbn_10_is_overridden, isbn_13_is_overridden, isbn_13_value,
                      publisher_is_overridden, publication_date_is_overridden,
                      page_count_is_overridden, language_is_overridden,
                      description_is_overridden)
                    values (?, false, false, false, false, false, true, '9780306406158',
                            false, false, false, false, false)
                    """,
                    linked.id().value()));
    assertTrue(
        PostgresConstraint.isCheckViolation(
            invalidOverride13, "user_edition_metadata_override_isbn_13_checksum"));
  }

  private void assertInvalidCanonicalIsbns(String isbn10, String isbn13, String constraint) {
    DataAccessException failure =
        assertThrows(
            DataAccessException.class,
            () ->
                dsl.insertInto(EDITION)
                    .set(EDITION.ID, UUID.randomUUID())
                    .set(EDITION.ISBN_10, isbn10)
                    .set(EDITION.ISBN_13, isbn13)
                    .set(EDITION.TITLE, "Invalid ISBN")
                    .set(EDITION.METADATA_ORIGIN, MetadataOrigin.MANUAL.name())
                    .set(EDITION.CREATED_AT, CREATED.atOffset(ZoneOffset.UTC))
                    .set(EDITION.UPDATED_AT, UPDATED.atOffset(ZoneOffset.UTC))
                    .execute());
    assertTrue(PostgresConstraint.isCheckViolation(failure, constraint));
  }

  private String effectiveTitle(UserEditionId id) {
    return dsl.select(USER_EDITION.EFFECTIVE_TITLE_SEARCH)
        .from(USER_EDITION)
        .where(USER_EDITION.ID.eq(id.value()))
        .fetchSingle(USER_EDITION.EFFECTIVE_TITLE_SEARCH);
  }

  private void assertInvalidPersistedState(
      UserEditionId id,
      String state,
      LocalDate startedOn,
      LocalDate finishedOn,
      String constraint) {
    DataAccessException failure =
        assertThrows(
            DataAccessException.class,
            () ->
                dsl.update(USER_EDITION)
                    .set(USER_EDITION.STATE, state)
                    .set(USER_EDITION.STARTED_ON, startedOn)
                    .set(USER_EDITION.FINISHED_ON, finishedOn)
                    .where(USER_EDITION.ID.eq(id.value()))
                    .execute());
    assertTrue(PostgresConstraint.isCheckViolation(failure, constraint));
  }

  private void assertInvalidPublicationDate(Integer year, Integer month, Integer day) {
    assertThrows(
        DataAccessException.class,
        () ->
            dsl.insertInto(EDITION)
                .set(EDITION.ID, UUID.randomUUID())
                .set(EDITION.TITLE, "Invalid date")
                .set(EDITION.PUBLICATION_YEAR, year)
                .set(EDITION.PUBLICATION_MONTH, month)
                .set(EDITION.PUBLICATION_DAY, day)
                .set(EDITION.METADATA_ORIGIN, MetadataOrigin.MANUAL.name())
                .set(EDITION.CREATED_AT, CREATED.atOffset(ZoneOffset.UTC))
                .set(EDITION.UPDATED_AT, UPDATED.atOffset(ZoneOffset.UTC))
                .execute());
  }

  private void insertUser(UserId id, String subject) {
    var users = com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER;
    dsl.insertInto(users)
        .set(users.ID, id.value())
        .set(users.OIDC_ISSUER, "https://issuer.example")
        .set(users.OIDC_SUBJECT, subject)
        .set(users.EMAIL, subject + "@example.com")
        .set(users.CREATED_AT, OffsetDateTime.ofInstant(CREATED, ZoneOffset.UTC))
        .set(users.UPDATED_AT, OffsetDateTime.ofInstant(UPDATED, ZoneOffset.UTC))
        .execute();
  }

  private static Edition edition(
      String isbn13,
      String providerName,
      String providerEditionId,
      PartialPublicationDate publicationDate,
      UUID coverId) {
    return editionWithIsbns(
        isbn13 == null
            ? CanonicalIsbns.none()
            : new CanonicalIsbns(Optional.empty(), Optional.of(Isbn13.parse(isbn13))),
        providerName,
        providerEditionId,
        publicationDate,
        coverId);
  }

  private static Edition editionWithIsbns(
      CanonicalIsbns isbns,
      String providerName,
      String providerEditionId,
      PartialPublicationDate publicationDate,
      UUID coverId) {
    return new Edition(
        new EditionId(UUID.randomUUID()),
        isbns,
        providerName,
        providerEditionId,
        "Canonical Title",
        "A subtitle",
        List.of("First Author", "Second Author"),
        "Hardcover",
        "Publisher",
        publicationDate,
        432,
        "en",
        "Description",
        coverId,
        providerName == null ? MetadataOrigin.MANUAL : MetadataOrigin.PROVIDER,
        CREATED,
        UPDATED);
  }

  private static UserEdition userEdition(EditionId editionId) {
    return userEdition(
        editionId, new Finished(Optional.of(LocalDate.of(2026, 1, 2)), LocalDate.of(2026, 8, 3)));
  }

  private static UserEdition userEdition(EditionId editionId, ReadingState state) {
    return new UserEdition(
        new UserEditionId(UUID.randomUUID()), editionId, state, "private notes", CREATED, UPDATED);
  }

  private static MetadataOverrides inheritedOverrides() {
    return MetadataOverrides.none();
  }
}
