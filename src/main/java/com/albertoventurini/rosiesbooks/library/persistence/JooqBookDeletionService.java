package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.api.BookDeletionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

@ApplicationScoped
class JooqBookDeletionService implements BookDeletionService {

  private final DSLContext dsl;

  JooqBookDeletionService(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<DeletionBook> find(CurrentUser owner, UUID userEditionId) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(userEditionId, "userEditionId");
    return dsl.select(
            USER_EDITION.ID,
            USER_EDITION.EFFECTIVE_TITLE_SEARCH,
            USER_EDITION.STATE,
            USER_EDITION.VERSION)
        .from(USER_EDITION)
        .where(USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.ID.eq(userEditionId)))
        .fetchOptional(
            row ->
                new DeletionBook(
                    row.get(USER_EDITION.ID),
                    row.get(USER_EDITION.EFFECTIVE_TITLE_SEARCH),
                    DeletionShelf.valueOf(row.get(USER_EDITION.STATE)),
                    row.get(USER_EDITION.VERSION)));
  }

  @Override
  @Transactional
  public DeletionResult delete(CurrentUser owner, UUID userEditionId, long expectedVersion) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(userEditionId, "userEditionId");

    Optional<UUID> editionId =
        dsl.select(USER_EDITION.EDITION_ID)
            .from(USER_EDITION)
            .where(
                USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.ID.eq(userEditionId)))
            .fetchOptional(USER_EDITION.EDITION_ID);
    if (editionId.isEmpty()) return notFound();

    var edition =
        dsl.select(EDITION.METADATA_ORIGIN, EDITION.PROVIDER_NAME, EDITION.PROVIDER_EDITION_ID)
            .from(EDITION)
            .where(EDITION.ID.eq(editionId.orElseThrow()))
            .forUpdate()
            .fetchOptional();
    if (edition.isEmpty()) return notFound();

    Optional<DeletionBook> loaded = find(owner, userEditionId);
    if (loaded.isEmpty()) return notFound();
    DeletionBook current = loaded.orElseThrow();
    if (current.version() != expectedVersion) {
      return new DeletionResult(DeletionStatus.CONFLICT, current);
    }

    int deleted =
        dsl.deleteFrom(USER_EDITION)
            .where(
                USER_EDITION
                    .USER_ID
                    .eq(owner.id().value())
                    .and(USER_EDITION.ID.eq(userEditionId))
                    .and(USER_EDITION.VERSION.eq(expectedVersion)))
            .execute();
    if (deleted != 1) {
      return find(owner, userEditionId)
          .map(book -> new DeletionResult(DeletionStatus.CONFLICT, book))
          .orElseGet(JooqBookDeletionService::notFound);
    }

    var canonical = edition.orElseThrow();
    boolean eligibleManualEdition =
        "MANUAL".equals(canonical.get(EDITION.METADATA_ORIGIN))
            && canonical.get(EDITION.PROVIDER_NAME) == null
            && canonical.get(EDITION.PROVIDER_EDITION_ID) == null;
    if (eligibleManualEdition
        && !dsl.fetchExists(
            dsl.selectOne()
                .from(USER_EDITION)
                .where(USER_EDITION.EDITION_ID.eq(editionId.orElseThrow())))) {
      dsl.deleteFrom(EDITION).where(EDITION.ID.eq(editionId.orElseThrow())).execute();
    }
    return new DeletionResult(DeletionStatus.DELETED, current);
  }

  private static DeletionResult notFound() {
    return new DeletionResult(DeletionStatus.NOT_FOUND, null);
  }
}
