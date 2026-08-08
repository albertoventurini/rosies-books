package com.albertoventurini.rosiesbooks.library.shelves;

import java.time.Year;
import java.util.List;
import java.util.Objects;

/** An owner-scoped Finished shelf for one selected calendar year. */
public record FinishedShelf(Year selectedYear, List<Year> availableYears, List<ShelfBook> books) {

  public FinishedShelf {
    Objects.requireNonNull(selectedYear, "selectedYear");
    availableYears = List.copyOf(availableYears);
    books = List.copyOf(books);
    if (!availableYears.contains(selectedYear)) {
      throw new IllegalArgumentException("selectedYear must be available");
    }
  }
}
