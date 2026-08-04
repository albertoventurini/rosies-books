package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.UserId;
import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

@ApplicationScoped
class UserEditionRepository {

  private final DSLContext dsl;

  UserEditionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  void link(UserId owner, UserEdition userEdition) {
    var canonical =
        dsl.select(EDITION.TITLE)
            .from(EDITION)
            .where(EDITION.ID.eq(userEdition.editionId().value()))
            .fetchOptional();
    if (canonical.isEmpty()) {
      throw new IllegalArgumentException("Unknown canonical edition");
    }
    String authors =
        String.join(
            " ",
            dsl.select(EDITION_AUTHOR.NAME)
                .from(EDITION_AUTHOR)
                .where(EDITION_AUTHOR.EDITION_ID.eq(userEdition.editionId().value()))
                .orderBy(EDITION_AUTHOR.POSITION)
                .fetch(EDITION_AUTHOR.NAME));
    try {
      dsl.insertInto(USER_EDITION)
          .set(USER_EDITION.ID, userEdition.id().value())
          .set(USER_EDITION.USER_ID, owner.value())
          .set(USER_EDITION.EDITION_ID, userEdition.editionId().value())
          .set(USER_EDITION.STATE, userEdition.state().name())
          .set(USER_EDITION.STARTED_ON, userEdition.startedOn())
          .set(USER_EDITION.FINISHED_ON, userEdition.finishedOn())
          .set(USER_EDITION.PRIVATE_NOTES, userEdition.privateNotes())
          .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, canonical.orElseThrow().value1())
          .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, authors)
          .set(USER_EDITION.CREATED_AT, atUtc(userEdition.createdAt()))
          .set(USER_EDITION.UPDATED_AT, atUtc(userEdition.updatedAt()))
          .execute();
    } catch (DataAccessException failure) {
      if (PostgresConstraint.isUniqueViolation(failure, "user_edition_user_edition_key")) {
        throw new EditionAlreadyLinkedException(failure);
      }
      throw failure;
    }
  }

  Optional<UserEdition> find(UserId owner, UserEditionId id) {
    return dsl.selectFrom(USER_EDITION)
        .where(USER_EDITION.USER_ID.eq(owner.value()).and(USER_EDITION.ID.eq(id.value())))
        .fetchOptional(
            row ->
                new UserEdition(
                    new UserEditionId(row.get(USER_EDITION.ID)),
                    new EditionId(row.get(USER_EDITION.EDITION_ID)),
                    ReadingState.valueOf(row.get(USER_EDITION.STATE)),
                    row.get(USER_EDITION.STARTED_ON),
                    row.get(USER_EDITION.FINISHED_ON),
                    row.get(USER_EDITION.PRIVATE_NOTES),
                    instant(row.get(USER_EDITION.CREATED_AT)),
                    instant(row.get(USER_EDITION.UPDATED_AT))));
  }

  Optional<EditionId> findEditionId(UserId owner, UserEditionId id) {
    return dsl.select(USER_EDITION.EDITION_ID)
        .from(USER_EDITION)
        .where(USER_EDITION.USER_ID.eq(owner.value()).and(USER_EDITION.ID.eq(id.value())))
        .fetchOptional(record -> new EditionId(record.value1()));
  }

  boolean updateSearchProjections(
      UserId owner, UserEditionId id, String effectiveTitle, String effectiveAuthors) {
    return dsl.update(USER_EDITION)
            .set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, effectiveTitle)
            .set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, effectiveAuthors)
            .where(USER_EDITION.USER_ID.eq(owner.value()).and(USER_EDITION.ID.eq(id.value())))
            .execute()
        == 1;
  }

  boolean delete(UserId owner, UserEditionId id) {
    return dsl.deleteFrom(USER_EDITION)
            .where(USER_EDITION.USER_ID.eq(owner.value()).and(USER_EDITION.ID.eq(id.value())))
            .execute()
        == 1;
  }

  private static OffsetDateTime atUtc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(OffsetDateTime value) {
    return value.toInstant();
  }
}
