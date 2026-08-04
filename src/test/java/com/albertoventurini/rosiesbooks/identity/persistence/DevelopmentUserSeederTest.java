package com.albertoventurini.rosiesbooks.identity.persistence;

import static com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DevelopmentUserSeederTest {

  @Inject DSLContext dsl;
  @Inject DevelopmentUserSeeder seeder;

  @AfterEach
  void restoreSeeds() {
    dsl.deleteFrom(APP_USER)
        .where(
            APP_USER.ID.in(
                DevelopmentUser.all().stream().map(u -> u.currentUser().id().value()).toList()))
        .execute();
    seeder.seed();
  }

  @Test
  void startupCreatesExactlyTheTwoExpectedDevelopmentUsers() {
    var stored =
        dsl.selectFrom(APP_USER)
            .where(
                APP_USER.ID.in(
                    DevelopmentUser.all().stream()
                        .map(user -> user.currentUser().id().value())
                        .toList()))
            .fetch();

    assertEquals(2, stored.size());
    for (DevelopmentUser expected : DevelopmentUser.all()) {
      var row =
          stored.stream()
              .filter(record -> record.get(APP_USER.ID).equals(expected.currentUser().id().value()))
              .findFirst()
              .orElseThrow();
      assertEquals(DevelopmentUser.OIDC_ISSUER, row.get(APP_USER.OIDC_ISSUER));
      assertEquals(expected.oidcSubject(), row.get(APP_USER.OIDC_SUBJECT));
      assertEquals(expected.email(), row.get(APP_USER.EMAIL));
      assertTrue(expected.email().endsWith(".invalid"));
    }
  }

  @Test
  void rerunningTheSeederIsIdempotent() {
    seeder.seed();
    seeder.seed();

    assertEquals(
        2,
        dsl.fetchCount(
            APP_USER,
            APP_USER.ID.in(
                DevelopmentUser.all().stream()
                    .map(user -> user.currentUser().id().value())
                    .toList())));
  }

  @Test
  void aConflictRollsBackAllSeedWrites() {
    dsl.deleteFrom(APP_USER)
        .where(
            APP_USER.ID.in(
                DevelopmentUser.all().stream()
                    .map(user -> user.currentUser().id().value())
                    .toList()))
        .execute();
    DevelopmentUser conflicting = DevelopmentUser.READER_TWO;
    OffsetDateTime timestamp = DevelopmentUser.CREATED_AT.atOffset(ZoneOffset.UTC);
    dsl.insertInto(APP_USER)
        .set(APP_USER.ID, conflicting.currentUser().id().value())
        .set(APP_USER.OIDC_ISSUER, DevelopmentUser.OIDC_ISSUER)
        .set(APP_USER.OIDC_SUBJECT, conflicting.oidcSubject())
        .set(APP_USER.EMAIL, "conflict@rosies-books.invalid")
        .set(APP_USER.CREATED_AT, timestamp)
        .set(APP_USER.UPDATED_AT, timestamp)
        .execute();

    assertThrows(IllegalStateException.class, seeder::seed);

    assertTrue(
        dsl.selectFrom(APP_USER)
            .where(APP_USER.ID.eq(DevelopmentUser.READER_ONE.currentUser().id().value()))
            .fetchOptional()
            .isEmpty());
    assertEquals(
        "conflict@rosies-books.invalid",
        dsl.select(APP_USER.EMAIL)
            .from(APP_USER)
            .where(APP_USER.ID.eq(conflicting.currentUser().id().value()))
            .fetchSingle(APP_USER.EMAIL));
  }
}
