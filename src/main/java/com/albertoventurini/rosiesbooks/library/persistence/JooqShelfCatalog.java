package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_AUTHOR_OVERRIDE;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_METADATA_OVERRIDE;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfBook;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfCatalog;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;

@ApplicationScoped
class JooqShelfCatalog implements ShelfCatalog {

  private final DSLContext dsl;

  JooqShelfCatalog(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<ShelfBook> find(CurrentUser owner, Shelf shelf) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(shelf, "shelf");
    Field<String> effectiveTitle =
        DSL.when(
                USER_EDITION_METADATA_OVERRIDE.TITLE_IS_OVERRIDDEN.eq(true),
                USER_EDITION_METADATA_OVERRIDE.TITLE_VALUE)
            .otherwise(EDITION.TITLE);

    return dsl.select(
            USER_EDITION.ID,
            effectiveTitle,
            USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN,
            USER_EDITION.STATE,
            USER_EDITION.STARTED_ON,
            USER_EDITION.FINISHED_ON,
            USER_EDITION.CREATED_AT)
        .from(USER_EDITION)
        .join(EDITION)
        .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
        .leftJoin(USER_EDITION_METADATA_OVERRIDE)
        .on(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID.eq(USER_EDITION.ID))
        .where(
            USER_EDITION
                .USER_ID
                .eq(owner.id().value())
                .and(USER_EDITION.STATE.eq(shelf.persistedState())))
        .orderBy(ordering(shelf))
        .fetch(
            row -> {
              UUID userEditionId = row.get(USER_EDITION.ID);
              boolean authorsOverridden =
                  Boolean.TRUE.equals(
                      row.get(USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN));
              return new ShelfBook(
                  new com.albertoventurini.rosiesbooks.library.internal.UserEditionId(
                      userEditionId),
                  row.get(effectiveTitle),
                  authorsOverridden
                      ? overriddenAuthors(owner, shelf, userEditionId)
                      : canonicalAuthors(owner, shelf, userEditionId),
                  readingState(
                      row.get(USER_EDITION.STATE),
                      row.get(USER_EDITION.STARTED_ON),
                      row.get(USER_EDITION.FINISHED_ON)),
                  row.get(USER_EDITION.CREATED_AT).toInstant());
            });
  }

  private static ReadingState readingState(
      String state, LocalDate startedOn, LocalDate finishedOn) {
    return switch (state) {
      case "TO_READ" -> new ToRead();
      case "READING" -> new Reading(startedOn);
      case "FINISHED" -> new Finished(Optional.ofNullable(startedOn), finishedOn);
      default -> throw new IllegalStateException("Unsupported persisted reading state: " + state);
    };
  }

  private List<String> canonicalAuthors(CurrentUser owner, Shelf shelf, UUID userEditionId) {
    return dsl.select(EDITION_AUTHOR.NAME)
        .from(EDITION_AUTHOR)
        .join(USER_EDITION)
        .on(USER_EDITION.EDITION_ID.eq(EDITION_AUTHOR.EDITION_ID))
        .leftJoin(USER_EDITION_METADATA_OVERRIDE)
        .on(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID.eq(USER_EDITION.ID))
        .where(
            USER_EDITION
                .ID
                .eq(userEditionId)
                .and(USER_EDITION.USER_ID.eq(owner.id().value()))
                .and(USER_EDITION.STATE.eq(shelf.persistedState()))
                .and(
                    DSL.coalesce(USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN, false)
                        .eq(false)))
        .orderBy(EDITION_AUTHOR.POSITION)
        .fetch(EDITION_AUTHOR.NAME);
  }

  private List<String> overriddenAuthors(CurrentUser owner, Shelf shelf, UUID userEditionId) {
    return dsl.select(USER_EDITION_AUTHOR_OVERRIDE.NAME)
        .from(USER_EDITION_AUTHOR_OVERRIDE)
        .join(USER_EDITION)
        .on(USER_EDITION.ID.eq(USER_EDITION_AUTHOR_OVERRIDE.USER_EDITION_ID))
        .join(USER_EDITION_METADATA_OVERRIDE)
        .on(USER_EDITION_METADATA_OVERRIDE.USER_EDITION_ID.eq(USER_EDITION.ID))
        .where(
            USER_EDITION
                .ID
                .eq(userEditionId)
                .and(USER_EDITION.USER_ID.eq(owner.id().value()))
                .and(USER_EDITION.STATE.eq(shelf.persistedState()))
                .and(USER_EDITION_METADATA_OVERRIDE.AUTHORS_IS_OVERRIDDEN.eq(true)))
        .orderBy(USER_EDITION_AUTHOR_OVERRIDE.POSITION)
        .fetch(USER_EDITION_AUTHOR_OVERRIDE.NAME);
  }

  private static List<? extends OrderField<?>> ordering(Shelf shelf) {
    return switch (shelf) {
      case READING -> List.of(USER_EDITION.STARTED_ON.desc(), USER_EDITION.ID.asc());
      case TO_READ -> List.of(USER_EDITION.CREATED_AT.desc(), USER_EDITION.ID.asc());
      case FINISHED -> List.of(USER_EDITION.FINISHED_ON.desc(), USER_EDITION.ID.asc());
    };
  }
}
