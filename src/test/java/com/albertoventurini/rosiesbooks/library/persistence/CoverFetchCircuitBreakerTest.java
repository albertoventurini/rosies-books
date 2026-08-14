package com.albertoventurini.rosiesbooks.library.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CoverFetchCircuitBreakerTest {

  @Test
  void opensAfterTheConfiguredConsecutiveFailuresAndClosesAfterCooldown() {
    CoverFetchCircuitBreaker breaker = new CoverFetchCircuitBreaker(3, Duration.ofMinutes(1));
    Instant now = Instant.parse("2026-08-14T05:00:00Z");

    breaker.failed(now);
    breaker.failed(now);
    assertTrue(breaker.allows(now));

    breaker.failed(now);
    assertFalse(breaker.allows(now.plusSeconds(59)));
    assertTrue(breaker.allows(now.plusSeconds(60)));
  }

  @Test
  void successfulRunResetsTheFailureCount() {
    CoverFetchCircuitBreaker breaker = new CoverFetchCircuitBreaker(2, Duration.ofMinutes(1));
    Instant now = Instant.parse("2026-08-14T05:00:00Z");

    breaker.failed(now);
    breaker.succeeded();
    breaker.failed(now);

    assertTrue(breaker.allows(now));
  }
}
