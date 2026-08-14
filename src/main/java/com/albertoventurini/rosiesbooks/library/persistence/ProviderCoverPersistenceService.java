package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import com.albertoventurini.rosiesbooks.provider.api.TrustedCoverReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Best-effort durable cover persistence, deliberately outside book confirmation transactions. */
@ApplicationScoped
public class ProviderCoverPersistenceService {
  private final DSLContext dsl;
  private final CoverAssetRepository assets;
  private final OpenLibraryCoverDownloader downloader;
  private final IsbnEditionLookup lookup;
  private final Clock clock;

  ProviderCoverPersistenceService(
      DSLContext dsl,
      CoverAssetRepository assets,
      OpenLibraryCoverDownloader downloader,
      IsbnEditionLookup lookup,
      Clock clock) {
    this.dsl = dsl;
    this.assets = assets;
    this.downloader = downloader;
    this.lookup = lookup;
    this.clock = clock;
  }

  @Transactional
  public void fetchAndAttach(UUID editionId, TrustedCoverReference source) {
    if (editionId == null || source == null) return;
    Instant now = Instant.now(clock);
    OpenLibraryCoverDownloader.Result result = downloader.download(source);
    if (result instanceof OpenLibraryCoverDownloader.Result.Success success) {
      UUID asset =
          assets.saveOrFind(
              success.content(),
              success.mimeType(),
              success.width(),
              success.height(),
              source.value().toString(),
              now);
      dsl.update(EDITION)
          .set(EDITION.COVER_ASSET_ID, asset)
          .set(EDITION.TRUSTED_COVER_SOURCE, source.value().toString())
          .set(EDITION.COVER_LAST_OUTCOME, "SUCCESS")
          .set(EDITION.COVER_LAST_ATTEMPTED_AT, now.atOffset(ZoneOffset.UTC))
          .where(EDITION.ID.eq(editionId))
          .execute();
    } else {
      dsl.update(EDITION)
          .set(EDITION.TRUSTED_COVER_SOURCE, source.value().toString())
          .set(EDITION.COVER_LAST_OUTCOME, "FAILED")
          .set(EDITION.COVER_LAST_ATTEMPTED_AT, now.atOffset(ZoneOffset.UTC))
          .where(EDITION.ID.eq(editionId))
          .execute();
    }
  }

  @Transactional
  public void retryIfCoverless(ProviderBookAdditionService.LocalEdition edition) {
    if (!edition.hasCover() && edition.trustedCoverSource().isPresent())
      fetchAndAttach(edition.id(), edition.trustedCoverSource().get());
  }

  /** Fetches a cover by exact ISBN for a durable task. */
  @Transactional
  public FetchOutcome fetchForIsbn(UUID editionId, String rawIsbn13) {
    if (editionId == null || rawIsbn13 == null) return new FetchOutcome.NoCover();
    boolean alreadyCovered =
        dsl.fetchExists(
            dsl.selectOne()
                .from(EDITION)
                .where(EDITION.ID.eq(editionId).and(EDITION.COVER_ASSET_ID.isNotNull())));
    if (alreadyCovered) return new FetchOutcome.Success();
    IsbnLookupResult result;
    try {
      result = lookup.lookup(new Isbn13(rawIsbn13));
    } catch (RuntimeException failure) {
      return new FetchOutcome.Retry(Optional.empty());
    }
    if (result instanceof IsbnLookupResult.NotFound) return new FetchOutcome.NoCover();
    if (result instanceof IsbnLookupResult.RateLimited limited)
      return new FetchOutcome.Retry(limited.retryAfter());
    if (!(result instanceof IsbnLookupResult.Found found))
      return new FetchOutcome.Retry(Optional.empty());
    if (found.edition().cover().isEmpty()) return new FetchOutcome.NoCover();
    fetchAndAttach(editionId, found.edition().cover().get());
    boolean attached =
        dsl.fetchExists(
            dsl.selectOne()
                .from(EDITION)
                .where(EDITION.ID.eq(editionId).and(EDITION.COVER_ASSET_ID.isNotNull())));
    return attached ? new FetchOutcome.Success() : new FetchOutcome.Retry(Optional.empty());
  }

  /** Refreshes only the requesting user's coverless linked edition, never its metadata. */
  @Transactional
  public void refresh(CurrentUser owner, UserEditionId userEditionId) {
    dsl.select(EDITION.ID, EDITION.ISBN_13, EDITION.TRUSTED_COVER_SOURCE, EDITION.COVER_ASSET_ID)
        .from(USER_EDITION)
        .join(EDITION)
        .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
        .where(
            USER_EDITION
                .ID
                .eq(userEditionId.value())
                .and(USER_EDITION.USER_ID.eq(owner.id().value()))
                .and(EDITION.COVER_ASSET_ID.isNull()))
        .fetchOptional()
        .ifPresent(
            row -> {
              UUID editionId = row.get(EDITION.ID);
              try {
                String source = row.get(EDITION.TRUSTED_COVER_SOURCE);
                if (source != null) {
                  fetchAndAttach(editionId, new TrustedCoverReference(java.net.URI.create(source)));
                  return;
                }
                String isbn = row.get(EDITION.ISBN_13);
                if (isbn == null) return;
                IsbnLookupResult result = lookup.lookup(new Isbn13(isbn));
                if (result instanceof IsbnLookupResult.Found found)
                  found.edition().cover().ifPresent(cover -> fetchAndAttach(editionId, cover));
              } catch (IllegalArgumentException ignored) {
                // A corrupt persisted source is not a destination that can be retried.
              }
            });
  }

  public sealed interface FetchOutcome
      permits FetchOutcome.Success, FetchOutcome.NoCover, FetchOutcome.Retry {
    record Success() implements FetchOutcome {}

    record NoCover() implements FetchOutcome {}

    record Retry(Optional<Duration> retryAfter) implements FetchOutcome {
      public Retry {
        retryAfter = retryAfter == null ? Optional.empty() : retryAfter;
      }
    }
  }
}
