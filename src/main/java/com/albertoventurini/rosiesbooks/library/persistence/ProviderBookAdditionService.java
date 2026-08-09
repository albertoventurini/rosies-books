package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.CanonicalIsbns;
import com.albertoventurini.rosiesbooks.library.internal.EditionId;
import com.albertoventurini.rosiesbooks.library.internal.EditionMetadata;
import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Isbn13;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ReadingState;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Persists an accepted provider result without ever refreshing an existing canonical edition. */
@ApplicationScoped
public class ProviderBookAdditionService {
  private final DSLContext dsl;
  private final EditionRepository editions;
  private final Clock clock;

  ProviderBookAdditionService(DSLContext dsl, EditionRepository editions, Clock clock) {
    this.dsl = dsl;
    this.editions = editions;
    this.clock = clock;
  }

  public Optional<LocalEdition> findByIsbn13(String normalizedIsbn13) {
    return editions.findByIsbn(Isbn13.parse(normalizedIsbn13)).map(LocalEdition::from);
  }

  public Optional<StoredCover> localCover(UUID editionId) {
    return dsl.select(COVER_ASSET.CONTENT, COVER_ASSET.MIME_TYPE)
        .from(EDITION)
        .join(COVER_ASSET).on(COVER_ASSET.ID.eq(EDITION.COVER_ASSET_ID))
        .where(EDITION.ID.eq(editionId))
        .fetchOptional(row -> new StoredCover(row.get(COVER_ASSET.CONTENT), row.get(COVER_ASSET.MIME_TYPE)));
  }

  @Transactional
  public AddedBook addLocal(CurrentUser owner, UUID editionId, ReadingState state) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(editionId, "editionId");
    lock("local:" + editionId);
    if (editions.find(new EditionId(editionId)).isEmpty()) throw new StaleReviewException();
    return link(owner, editionId, state);
  }

  @Transactional
  public AddedBook addProvider(CurrentUser owner, ProviderCandidate candidate, ReadingState state) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(candidate, "candidate");
    Objects.requireNonNull(state, "state");
    lock("provider:" + candidate.lookupIsbn13() + ':' + candidate.providerName() + ':' + candidate.providerEditionId());
    Optional<Edition> byIsbn = editions.findByIsbn(Isbn13.parse(candidate.lookupIsbn13()));
    Optional<Edition> byProvider = findByProvider(candidate.providerName(), candidate.providerEditionId());
    if (byIsbn.isPresent() && byProvider.isPresent() && !byIsbn.get().id().equals(byProvider.get().id()))
      throw new IdentifierConflictException();
    Edition edition = byIsbn.or(() -> byProvider).orElseGet(() -> create(candidate));
    return link(owner, edition.id().value(), state);
  }

  private Edition create(ProviderCandidate candidate) {
    EditionMetadata metadata = candidate.metadata();
    CanonicalIsbns isbns = new CanonicalIsbns(metadata.isbn10(), Optional.of(Isbn13.parse(candidate.lookupIsbn13())));
    Instant now = Instant.now(clock);
    Edition edition = new Edition(new EditionId(UUID.randomUUID()), isbns, candidate.providerName(), candidate.providerEditionId(), metadata.title(), metadata.subtitle().orElse(null), metadata.authors(), metadata.format().orElse(null), metadata.publisher().orElse(null), metadata.publicationDate().orElse(com.albertoventurini.rosiesbooks.library.internal.PartialPublicationDate.unknown()), metadata.pageCount().orElse(null), metadata.language().orElse(null), metadata.description().orElse(null), null, MetadataOrigin.PROVIDER, now, now);
    editions.create(edition);
    return edition;
  }

  private Optional<Edition> findByProvider(String name, String id) {
    return dsl.select(EDITION.ID).from(EDITION)
        .where(EDITION.PROVIDER_NAME.eq(name).and(EDITION.PROVIDER_EDITION_ID.eq(id)))
        .fetchOptional(row -> new EditionId(row.get(EDITION.ID))).flatMap(editions::find);
  }

  private AddedBook link(CurrentUser owner, UUID editionId, ReadingState state) {
    AddedBook existing = dsl.select(USER_EDITION.ID, USER_EDITION.STATE).from(USER_EDITION)
        .where(USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.EDITION_ID.eq(editionId)))
        .fetchOne(row -> new AddedBook(new UserEditionId(row.get(USER_EDITION.ID)), row.get(USER_EDITION.STATE)));
    if (existing != null) return existing;
    StateColumns fields = columns(state);
    var canonical = dsl.select(EDITION.TITLE).from(EDITION).where(EDITION.ID.eq(editionId)).fetchSingle();
    String authors = String.join(" ", dsl.select(EDITION_AUTHOR.NAME).from(EDITION_AUTHOR)
        .where(EDITION_AUTHOR.EDITION_ID.eq(editionId)).orderBy(EDITION_AUTHOR.POSITION).fetch(EDITION_AUTHOR.NAME));
    Instant now = Instant.now(clock);
    UUID id = UUID.randomUUID();
    var inserted = dsl.insertInto(USER_EDITION).set(USER_EDITION.ID, id).set(USER_EDITION.USER_ID, owner.id().value())
        .set(USER_EDITION.EDITION_ID, editionId).set(USER_EDITION.STATE, fields.state()).set(USER_EDITION.STARTED_ON, fields.startedOn()).set(USER_EDITION.FINISHED_ON, fields.finishedOn())
        .set(USER_EDITION.PRIVATE_NOTES, (String) null).set(USER_EDITION.EFFECTIVE_TITLE_SEARCH, canonical.value1()).set(USER_EDITION.EFFECTIVE_AUTHORS_SEARCH, authors)
        .set(USER_EDITION.CREATED_AT, now.atOffset(ZoneOffset.UTC)).set(USER_EDITION.UPDATED_AT, now.atOffset(ZoneOffset.UTC))
        .onConflict(USER_EDITION.USER_ID, USER_EDITION.EDITION_ID).doNothing().returning(USER_EDITION.ID, USER_EDITION.STATE)
        .fetchOne(row -> new AddedBook(new UserEditionId(row.get(USER_EDITION.ID)), row.get(USER_EDITION.STATE)));
    if (inserted != null) return inserted;
    return Objects.requireNonNull(dsl.select(USER_EDITION.ID, USER_EDITION.STATE).from(USER_EDITION)
        .where(USER_EDITION.USER_ID.eq(owner.id().value()).and(USER_EDITION.EDITION_ID.eq(editionId)))
        .fetchOne(row -> new AddedBook(new UserEditionId(row.get(USER_EDITION.ID)), row.get(USER_EDITION.STATE))));
  }

  private void lock(String key) { dsl.fetch("select pg_advisory_xact_lock(hashtextextended(?, 0))", key); }
  private static StateColumns columns(ReadingState state) {
    return switch (state) { case ToRead ignored -> new StateColumns("TO_READ", null, null); case Reading reading -> new StateColumns("READING", reading.startedOn(), null); case Finished finished -> new StateColumns("FINISHED", finished.startedOn().orElse(null), finished.finishedOn()); };
  }
  public record ProviderCandidate(String lookupIsbn13, String providerName, String providerEditionId, EditionMetadata metadata) {
    public ProviderCandidate { Objects.requireNonNull(lookupIsbn13); providerName = providerName == null ? "" : providerName.strip().toLowerCase(java.util.Locale.ROOT); providerEditionId = providerEditionId == null ? "" : providerEditionId.strip(); Objects.requireNonNull(metadata); if (providerName.isEmpty() || providerEditionId.isEmpty()) throw new IllegalArgumentException("Provider identity is required"); Isbn13.parse(lookupIsbn13); }
  }
  public record LocalEdition(UUID id, EditionMetadata metadata, boolean hasCover) {
    static LocalEdition from(Edition edition) { return new LocalEdition(edition.id().value(), edition.metadata(), edition.coverAssetId() != null); }
  }
  public record StoredCover(byte[] content, String mimeType) {
    public StoredCover { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
  }
  public record AddedBook(UserEditionId id, String state) {}
  public static class StaleReviewException extends RuntimeException {}
  public static class IdentifierConflictException extends RuntimeException {}
  private record StateColumns(String state, LocalDate startedOn, LocalDate finishedOn) {}
}
