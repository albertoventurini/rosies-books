package com.albertoventurini.rosiesbooks.identity.persistence;

import static com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UserPersistenceTest {

  private static final Instant CREATED = Instant.parse("2026-08-03T10:15:30Z");
  private static final Instant UPDATED = Instant.parse("2026-08-03T11:15:30Z");

  @Inject UserRepository repository;
  @Inject DSLContext dsl;
  private final Set<UUID> createdUserIds = new HashSet<>();

  @AfterEach
  void removeUsers() {
    dsl.deleteFrom(APP_USER).where(APP_USER.ID.in(createdUserIds)).execute();
    createdUserIds.clear();
  }

  @Test
  void createsFindsAndDeletesAUserWithCallerSuppliedUtcTimestamps() {
    User user = user("subject-1");
    createdUserIds.add(user.id().value());

    repository.create(user);

    assertEquals(user, repository.find(user.id()).orElseThrow());
    assertTrue(repository.delete(user.id()));
    assertTrue(repository.find(user.id()).isEmpty());
  }

  @Test
  void mapsOnlyTheNamedOidcIdentityConflict() {
    User first = user("same-subject");
    createdUserIds.add(first.id().value());
    repository.create(first);

    assertThrows(
        DuplicateOidcIdentityException.class,
        () ->
            repository.create(
                new User(
                    new UserId(UUID.randomUUID()),
                    first.oidcIssuer(),
                    first.oidcSubject(),
                    "different@example.com",
                    CREATED,
                    UPDATED)));

    User invalid =
        new User(
            new UserId(UUID.randomUUID()),
            "issuer",
            "another-subject",
            "NOT-NORMALIZED@example.com",
            CREATED,
            UPDATED);
    assertThrows(DataAccessException.class, () -> repository.create(invalid));
  }

  private static User user(String subject) {
    return new User(
        new UserId(UUID.randomUUID()),
        "https://issuer.example",
        subject,
        "reader@example.com",
        CREATED,
        UPDATED);
  }
}
