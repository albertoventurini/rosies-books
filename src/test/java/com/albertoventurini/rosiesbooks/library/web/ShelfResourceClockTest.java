package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ShelfResourceClockTest {

  @Test
  void derivesTheCurrentYearFromTheConfiguredZoneRatherThanUtcOrTheJvmZone() {
    Clock clock = Clock.fixed(Instant.parse("2025-12-31T22:30:00Z"), ZoneOffset.UTC);
    ShelfResource resource = new ShelfResource(null, null, clock, "Africa/Johannesburg");

    assertEquals(Year.of(2026), resource.currentYear());
  }
}
