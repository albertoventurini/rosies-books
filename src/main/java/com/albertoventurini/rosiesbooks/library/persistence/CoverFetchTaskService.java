package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_FETCH_TASK;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.library.internal.UserEditionId;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Durable, owner-scoped asynchronous fetching for imported and manually requested covers. */
@ApplicationScoped
public class CoverFetchTaskService {
  private static final Logger LOG = Logger.getLogger(CoverFetchTaskService.class.getName());
  private static final int MAX_ATTEMPTS = 3;
  private final DSLContext dsl;
  private final ProviderCoverPersistenceService covers;
  private final Clock clock;
  private final CoverFetchCircuitBreaker circuitBreaker;

  CoverFetchTaskService(
      DSLContext dsl,
      ProviderCoverPersistenceService covers,
      Clock clock,
      CoverFetchCircuitBreaker circuitBreaker) {
    this.dsl = dsl;
    this.covers = covers;
    this.clock = clock;
    this.circuitBreaker = circuitBreaker;
  }

  @Transactional
  public void enqueueImport(CurrentUser owner, UUID requestId, UUID userEditionId) {
    if (eligible(owner.id().value(), userEditionId))
      insert(owner.id().value(), userEditionId, requestId);
  }

  /** Enqueues or explicitly reactivates a task, but never performs provider I/O in the request. */
  @Transactional
  public void request(CurrentUser owner, UserEditionId userEditionId) {
    UUID userId = owner.id().value();
    if (!eligible(userId, userEditionId.value())) return;
    Instant now = Instant.now(clock);
    int changed =
        dsl.update(COVER_FETCH_TASK)
            .set(COVER_FETCH_TASK.STATUS, "PENDING")
            .set(COVER_FETCH_TASK.NEXT_ATTEMPT_AT, utc(now))
            .set(COVER_FETCH_TASK.LEASE_UNTIL, (OffsetDateTime) null)
            .set(COVER_FETCH_TASK.COMPLETED_AT, (OffsetDateTime) null)
            .where(
                COVER_FETCH_TASK
                    .USER_EDITION_ID
                    .eq(userEditionId.value())
                    .and(COVER_FETCH_TASK.USER_ID.eq(userId))
                    .and(COVER_FETCH_TASK.STATUS.eq("NO_COVER")))
            .execute();
    if (changed == 0) insert(userId, userEditionId.value(), null);
  }

  public Progress progress(CurrentUser owner, UUID requestId) {
    int pending = count(owner.id().value(), requestId, "PENDING", "RETRY");
    int processing = count(owner.id().value(), requestId, "PROCESSING");
    int downloaded = count(owner.id().value(), requestId, "SUCCEEDED");
    int unavailable = count(owner.id().value(), requestId, "NO_COVER");
    return new Progress(pending, processing, downloaded, unavailable);
  }

  @Scheduled(
      every = "${rosies-books.cover-fetch.poll-interval:1s}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void runOne() {
    Instant now = Instant.now(clock);
    if (!circuitBreaker.allows(now)) return;
    try {
      claim().ifPresent(this::process);
      circuitBreaker.succeeded();
    } catch (RuntimeException failure) {
      circuitBreaker.failed(now);
      LOG.log(Level.WARNING, "Cover-fetch worker infrastructure failure", failure);
    }
  }

  Optional<Claim> claim() {
    return dsl.transactionResult(configuration -> claim(DSL.using(configuration)));
  }

  private Optional<Claim> claim(DSLContext transactional) {
    Instant now = Instant.now(clock);
    OffsetDateTime at = utc(now);
    return transactional
        .select(COVER_FETCH_TASK.ID, COVER_FETCH_TASK.USER_EDITION_ID, EDITION.ID, EDITION.ISBN_13)
        .from(COVER_FETCH_TASK)
        .join(USER_EDITION)
        .on(USER_EDITION.ID.eq(COVER_FETCH_TASK.USER_EDITION_ID))
        .join(EDITION)
        .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
        .where(
            COVER_FETCH_TASK
                .STATUS
                .in("PENDING", "RETRY")
                .and(COVER_FETCH_TASK.NEXT_ATTEMPT_AT.le(at))
                .or(
                    COVER_FETCH_TASK
                        .STATUS
                        .eq("PROCESSING")
                        .and(COVER_FETCH_TASK.LEASE_UNTIL.lt(at)))
                .and(EDITION.ISBN_13.isNotNull()))
        .orderBy(COVER_FETCH_TASK.NEXT_ATTEMPT_AT.asc())
        .limit(1)
        .forUpdate()
        .skipLocked()
        .fetchOptional(
            row ->
                new Claim(
                    row.get(COVER_FETCH_TASK.ID), row.get(EDITION.ID), row.get(EDITION.ISBN_13)))
        .map(
            claim -> {
              transactional
                  .update(COVER_FETCH_TASK)
                  .set(COVER_FETCH_TASK.STATUS, "PROCESSING")
                  .set(COVER_FETCH_TASK.ATTEMPT_COUNT, COVER_FETCH_TASK.ATTEMPT_COUNT.plus(1))
                  .set(COVER_FETCH_TASK.LEASE_UNTIL, utc(now.plusSeconds(30)))
                  .where(COVER_FETCH_TASK.ID.eq(claim.taskId()))
                  .execute();
              return claim;
            });
  }

  private void process(Claim claim) {
    ProviderCoverPersistenceService.FetchOutcome outcome;
    try {
      outcome = covers.fetchForIsbn(claim.editionId(), claim.isbn13());
    } catch (RuntimeException failure) {
      outcome = new ProviderCoverPersistenceService.FetchOutcome.Retry(Optional.empty());
    }
    complete(claim.taskId(), outcome);
  }

  void complete(UUID taskId, ProviderCoverPersistenceService.FetchOutcome outcome) {
    dsl.transaction(configuration -> complete(DSL.using(configuration), taskId, outcome));
  }

  private void complete(
      DSLContext transactional, UUID taskId, ProviderCoverPersistenceService.FetchOutcome outcome) {
    Instant now = Instant.now(clock);
    if (outcome instanceof ProviderCoverPersistenceService.FetchOutcome.Success) {
      terminal(transactional, taskId, "SUCCEEDED", now);
    } else if (outcome instanceof ProviderCoverPersistenceService.FetchOutcome.NoCover) {
      terminal(transactional, taskId, "NO_COVER", now);
    } else {
      int attempts =
          transactional
              .select(COVER_FETCH_TASK.ATTEMPT_COUNT)
              .from(COVER_FETCH_TASK)
              .where(COVER_FETCH_TASK.ID.eq(taskId))
              .fetchOptional(COVER_FETCH_TASK.ATTEMPT_COUNT)
              .orElse(1);
      if (attempts >= MAX_ATTEMPTS) {
        terminal(transactional, taskId, "NO_COVER", now);
        return;
      }
      Duration fallback = Duration.ofMinutes(1).multipliedBy(1L << Math.min(attempts - 1, 6));
      Duration delay =
          ((ProviderCoverPersistenceService.FetchOutcome.Retry) outcome)
              .retryAfter()
              .orElse(fallback);
      transactional
          .update(COVER_FETCH_TASK)
          .set(COVER_FETCH_TASK.STATUS, "RETRY")
          .set(COVER_FETCH_TASK.NEXT_ATTEMPT_AT, utc(now.plus(delay)))
          .set(COVER_FETCH_TASK.LEASE_UNTIL, (OffsetDateTime) null)
          .where(COVER_FETCH_TASK.ID.eq(taskId))
          .execute();
    }
  }

  private void terminal(DSLContext transactional, UUID taskId, String status, Instant now) {
    transactional
        .update(COVER_FETCH_TASK)
        .set(COVER_FETCH_TASK.STATUS, status)
        .set(COVER_FETCH_TASK.LEASE_UNTIL, (OffsetDateTime) null)
        .set(COVER_FETCH_TASK.COMPLETED_AT, utc(now))
        .where(COVER_FETCH_TASK.ID.eq(taskId))
        .execute();
  }

  private boolean eligible(UUID owner, UUID userEditionId) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(USER_EDITION)
            .join(EDITION)
            .on(EDITION.ID.eq(USER_EDITION.EDITION_ID))
            .where(
                USER_EDITION
                    .ID
                    .eq(userEditionId)
                    .and(USER_EDITION.USER_ID.eq(owner))
                    .and(EDITION.COVER_ASSET_ID.isNull())
                    .and(EDITION.ISBN_13.isNotNull())));
  }

  private void insert(UUID owner, UUID userEditionId, UUID importId) {
    Instant now = Instant.now(clock);
    dsl.insertInto(COVER_FETCH_TASK)
        .columns(
            COVER_FETCH_TASK.ID,
            COVER_FETCH_TASK.USER_ID,
            COVER_FETCH_TASK.USER_EDITION_ID,
            COVER_FETCH_TASK.GOODREADS_REQUEST_ID,
            COVER_FETCH_TASK.STATUS,
            COVER_FETCH_TASK.ATTEMPT_COUNT,
            COVER_FETCH_TASK.NEXT_ATTEMPT_AT)
        .values(UUID.randomUUID(), owner, userEditionId, importId, "PENDING", 0, utc(now))
        .onConflict(COVER_FETCH_TASK.USER_EDITION_ID)
        .doNothing()
        .execute();
  }

  private int count(UUID owner, UUID request, String... statuses) {
    return dsl.fetchCount(
        COVER_FETCH_TASK,
        COVER_FETCH_TASK
            .USER_ID
            .eq(owner)
            .and(COVER_FETCH_TASK.GOODREADS_REQUEST_ID.eq(request))
            .and(COVER_FETCH_TASK.STATUS.in(statuses)));
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  record Claim(UUID taskId, UUID editionId, String isbn13) {}

  public record Progress(int pending, int processing, int downloaded, int unavailable) {
    public boolean outstanding() {
      return pending + processing > 0;
    }
  }
}
