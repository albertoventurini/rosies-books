package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import com.albertoventurini.rosiesbooks.provider.api.TrustedCoverReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;

/** Best-effort durable cover persistence, deliberately outside book confirmation transactions. */
@ApplicationScoped
public class ProviderCoverPersistenceService {
  private final DSLContext dsl;
  private final CoverAssetRepository assets;
  private final OpenLibraryCoverDownloader downloader;
  private final Clock clock;

  ProviderCoverPersistenceService(
      DSLContext dsl,
      CoverAssetRepository assets,
      OpenLibraryCoverDownloader downloader,
      Clock clock) {
    this.dsl = dsl;
    this.assets = assets;
    this.downloader = downloader;
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

  /** Retries only the requesting user's linked edition and only after a recorded failure. */
  @Transactional
  public void retryFailed(CurrentUser owner, UserEditionId userEditionId) {
    dsl.select(EDITION.ID, EDITION.TRUSTED_COVER_SOURCE)
        .from(USER_EDITION)
        .join(EDITION)
        .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
        .where(
            USER_EDITION.ID.eq(userEditionId.value())
                .and(USER_EDITION.USER_ID.eq(owner.id().value()))
                .and(EDITION.COVER_ASSET_ID.isNull())
                .and(EDITION.COVER_LAST_OUTCOME.eq("FAILED")))
        .fetchOptional()
        .ifPresent(
            row -> {
              try {
                fetchAndAttach(
                    row.get(EDITION.ID),
                    new TrustedCoverReference(
                        java.net.URI.create(row.get(EDITION.TRUSTED_COVER_SOURCE))));
              } catch (IllegalArgumentException ignored) {
                // A corrupt persisted source is not a destination that can be retried.
              }
            });
  }
}
