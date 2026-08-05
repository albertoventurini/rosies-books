package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.albertoventurini.rosiesbooks.library.internal.Finished;
import com.albertoventurini.rosiesbooks.library.internal.Reading;
import com.albertoventurini.rosiesbooks.library.internal.ToRead;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShelfDatePresentationTest {

  private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
  private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);

  @Test
  void presentsStateSpecificReadingAndFinishedDates() {
    assertEquals(
        "Started 3 Aug 2026",
        ShelfDatePresentation.contextLine(
            new Reading(LocalDate.of(2026, 8, 3)), Instant.EPOCH, TODAY, ZONE));
    assertEquals(
        "Finished 1 Jul 2025",
        ShelfDatePresentation.contextLine(
            new Finished(Optional.empty(), LocalDate.of(2025, 7, 1)), Instant.EPOCH, TODAY, ZONE));
  }

  @Test
  void presentsFixedAddedAgeBoundaries() {
    assertAge(0, "Added today");
    assertAge(1, "Added 1 day ago");
    assertAge(6, "Added 6 days ago");
    assertAge(7, "Added 1 week ago");
    assertAge(29, "Added 4 weeks ago");
    assertAge(30, "Added 1 month ago");
    assertAge(364, "Added 12 months ago");
    assertAge(365, "Added 1 year ago");
    assertAge(800, "Added 2 years ago");
  }

  @Test
  void usesTheConfiguredZoneAndClampsFutureTimestamps() {
    assertEquals(
        "Added today",
        ShelfDatePresentation.contextLine(
            new ToRead(), Instant.parse("2026-08-03T22:30:00Z"), TODAY, ZONE));
    assertEquals(
        "Added today",
        ShelfDatePresentation.contextLine(
            new ToRead(), Instant.parse("2026-08-05T00:00:00Z"), TODAY, ZONE));
  }

  private static void assertAge(long days, String expected) {
    Instant addedAt = TODAY.minusDays(days).atStartOfDay(ZONE).toInstant();
    assertEquals(expected, ShelfDatePresentation.contextLine(new ToRead(), addedAt, TODAY, ZONE));
  }
}
