package com.albertoventurini.rosiesbooks.library.persistence;

import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;

/** Stops repeated worker infrastructure failures from producing a tight error loop. */
@ApplicationScoped
class CoverFetchCircuitBreaker {
  private final int failureThreshold;
  private final Duration cooldown;
  private int consecutiveFailures;
  private Instant openUntil = Instant.MIN;

  @Inject
  CoverFetchCircuitBreaker(CoverFetchCircuitBreakerConfig config) {
    this(config.failureThreshold(), config.cooldown());
  }

  CoverFetchCircuitBreaker(int failureThreshold, Duration cooldown) {
    if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold");
    if (cooldown.isNegative() || cooldown.isZero()) throw new IllegalArgumentException("cooldown");
    this.failureThreshold = failureThreshold;
    this.cooldown = cooldown;
  }

  synchronized boolean allows(Instant now) {
    return !now.isBefore(openUntil);
  }

  synchronized void succeeded() {
    consecutiveFailures = 0;
    openUntil = Instant.MIN;
  }

  synchronized void failed(Instant now) {
    consecutiveFailures++;
    if (consecutiveFailures >= failureThreshold) openUntil = now.plus(cooldown);
  }
}

@ConfigMapping(prefix = "rosies-books.cover-fetch.circuit-breaker")
interface CoverFetchCircuitBreakerConfig {
  int failureThreshold();

  Duration cooldown();
}
