package com.albertoventurini.rosiesbooks.library.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartialPublicationDateTest {

  @Test
  void representsEverySupportedPrecisionWithoutInventingComponents() {
    assertEquals(new PartialPublicationDate(null, null, null), PartialPublicationDate.unknown());
    assertEquals(new PartialPublicationDate(2026, null, null), PartialPublicationDate.year(2026));
    assertEquals(
        new PartialPublicationDate(2026, 8, null), PartialPublicationDate.yearMonth(2026, 8));
    assertEquals(new PartialPublicationDate(2024, 2, 29), PartialPublicationDate.full(2024, 2, 29));
  }

  @Test
  void rejectsUnsupportedOrInvalidComponentCombinations() {
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(0, null, null));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(10_000, null, null));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(2026, 0, null));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(2026, 13, null));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(2026, null, 1));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(null, 8, null));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(2026, 2, 29));
    assertThrows(DateTimeException.class, () -> new PartialPublicationDate(2024, 4, 31));
  }

  @Test
  void ordersKnownComponentsLexicallyThenByPrecisionAndUnknownLast() {
    var dates =
        new ArrayList<>(
            List.of(
                PartialPublicationDate.unknown(),
                PartialPublicationDate.full(2026, 8, 3),
                PartialPublicationDate.yearMonth(2025, 12),
                PartialPublicationDate.full(2026, 1, 1),
                PartialPublicationDate.year(2026),
                PartialPublicationDate.yearMonth(2026, 8),
                PartialPublicationDate.year(2025)));

    dates.sort(null);

    assertEquals(
        List.of(
            PartialPublicationDate.year(2025),
            PartialPublicationDate.yearMonth(2025, 12),
            PartialPublicationDate.year(2026),
            PartialPublicationDate.full(2026, 1, 1),
            PartialPublicationDate.yearMonth(2026, 8),
            PartialPublicationDate.full(2026, 8, 3),
            PartialPublicationDate.unknown()),
        dates);
    assertEquals(
        0,
        PartialPublicationDate.yearMonth(2026, 8)
            .compareTo(new PartialPublicationDate(2026, 8, null)));
    assertTrue(PartialPublicationDate.unknown().compareTo(PartialPublicationDate.year(9999)) > 0);
  }
}
