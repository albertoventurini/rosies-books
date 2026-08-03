package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
            "9781234567890",
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
            OverrideValue.overridden("Private title"),
            OverrideValue.overridden(null),
            OverrideValue.overridden(List.of("Second Author", "First Author")),
            OverrideValue.inherited(),
            OverrideValue.overridden("123456789X"),
            OverrideValue.overridden(null),
            OverrideValue.overridden("Private publisher"),
            OverrideValue.overridden(PartialPublicationDate.yearMonth(2020, 7)),
            OverrideValue.overridden(321),
            OverrideValue.inherited(),
            OverrideValue.overridden(null));
    assertTrue(coordinator.saveOverrides(firstUser, linked.id(), metadataOverrides));
    assertEquals(metadataOverrides, overrides.find(firstUser, linked.id()).orElseThrow());
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
                OverrideValue.overridden("stolen"),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited(),
                OverrideValue.inherited())));
    assertEquals(original, overrides.find(firstUser, linked.id()).orElseThrow());
    assertFalse(userEditions.delete(secondUser, linked.id()));
    assertTrue(userEditions.find(firstUser, linked.id()).isPresent());
  }

  @Test
  void mapsIdentifierAndPerUserLinkConflictsWhileAllowingTwoOwners() {
    Edition original =
        edition("9781111111111", "catalog", "Mixed-Case-1", PartialPublicationDate.unknown(), null);
    coordinator.createEdition(original);
    assertThrows(
        DuplicateIsbn13Exception.class,
        () ->
            coordinator.createEdition(
                edition(original.isbn13(), null, null, PartialPublicationDate.unknown(), null)));
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
    return new Edition(
        new EditionId(UUID.randomUUID()),
        "123456789X",
        isbn13,
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
    return new UserEdition(
        new UserEditionId(UUID.randomUUID()),
        editionId,
        ReadingState.FINISHED,
        LocalDate.of(2026, 1, 2),
        LocalDate.of(2026, 8, 3),
        "private notes",
        CREATED,
        UPDATED);
  }

  private static MetadataOverrides inheritedOverrides() {
    return new MetadataOverrides(
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited(),
        OverrideValue.inherited());
  }
}
