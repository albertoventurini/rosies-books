package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StateChangeResourceClockTest {

  @Test
  void derivesTodayFromTheConfiguredZoneWhenItDiffersFromUtc() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-03T22:30:00Z"), ZoneOffset.UTC);
    StateChangeResource resource =
        new StateChangeResource(null, null, clock, "Africa/Johannesburg");

    assertEquals(LocalDate.of(2026, 8, 4), resource.today());
  }
}
