package com.albertoventurini.rosiesbooks.identity.persistence;

import static com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables.APP_USER;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.postgresql.util.PSQLException;

@ApplicationScoped
class UserRepository {

  private final DSLContext dsl;

  UserRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  void create(User user) {
    try {
      dsl.insertInto(APP_USER)
          .set(APP_USER.ID, user.id().value())
          .set(APP_USER.OIDC_ISSUER, user.oidcIssuer())
          .set(APP_USER.OIDC_SUBJECT, user.oidcSubject())
          .set(APP_USER.EMAIL, user.email())
          .set(APP_USER.CREATED_AT, atUtc(user.createdAt()))
          .set(APP_USER.UPDATED_AT, atUtc(user.updatedAt()))
          .execute();
    } catch (DataAccessException failure) {
      if (isUniqueViolation(failure, "app_user_oidc_identity_key")) {
        throw new DuplicateOidcIdentityException(failure);
      }
      throw failure;
    }
  }

  Optional<User> find(UserId id) {
    return dsl.selectFrom(APP_USER)
        .where(APP_USER.ID.eq(id.value()))
        .fetchOptional(UserRepository::user);
  }

  Optional<User> findByOidcIdentity(String issuer, String subject) {
    return dsl.selectFrom(APP_USER)
        .where(APP_USER.OIDC_ISSUER.eq(issuer))
        .and(APP_USER.OIDC_SUBJECT.eq(subject))
        .fetchOptional(UserRepository::user);
  }

  boolean createIfAbsent(User user) {
    return dsl.insertInto(APP_USER)
            .set(APP_USER.ID, user.id().value())
            .set(APP_USER.OIDC_ISSUER, user.oidcIssuer())
            .set(APP_USER.OIDC_SUBJECT, user.oidcSubject())
            .set(APP_USER.EMAIL, user.email())
            .set(APP_USER.CREATED_AT, atUtc(user.createdAt()))
            .set(APP_USER.UPDATED_AT, atUtc(user.updatedAt()))
            .onConflict(APP_USER.OIDC_ISSUER, APP_USER.OIDC_SUBJECT)
            .doNothing()
            .execute()
        == 1;
  }

  void updateEmail(UserId id, String email, java.time.Instant updatedAt) {
    dsl.update(APP_USER)
        .set(APP_USER.EMAIL, email)
        .set(APP_USER.UPDATED_AT, atUtc(updatedAt))
        .where(APP_USER.ID.eq(id.value()))
        .execute();
  }

  boolean delete(UserId id) {
    return dsl.deleteFrom(APP_USER).where(APP_USER.ID.eq(id.value())).execute() == 1;
  }

  private static OffsetDateTime atUtc(java.time.Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  private static User user(org.jooq.Record row) {
    return new User(
        new UserId(row.get(APP_USER.ID)),
        row.get(APP_USER.OIDC_ISSUER),
        row.get(APP_USER.OIDC_SUBJECT),
        row.get(APP_USER.EMAIL),
        row.get(APP_USER.CREATED_AT).toInstant(),
        row.get(APP_USER.UPDATED_AT).toInstant());
  }

  private static boolean isUniqueViolation(DataAccessException failure, String constraint) {
    PSQLException postgres = failure.getCause(PSQLException.class);
    return postgres != null
        && "23505".equals(postgres.getSQLState())
        && postgres.getServerErrorMessage() != null
        && constraint.equals(postgres.getServerErrorMessage().getConstraint());
  }
}
