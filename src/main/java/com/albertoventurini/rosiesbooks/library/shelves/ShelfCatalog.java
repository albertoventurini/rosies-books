package com.albertoventurini.rosiesbooks.library.shelves;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import java.time.Year;
import java.util.List;
import java.util.Optional;

/** Owner-scoped shelf query boundary. */
public interface ShelfCatalog {

  List<ShelfBook> find(CurrentUser owner, Shelf shelf);

  /**
   * Finds an owner's Finished books for {@code selectedYear}, deriving available years from that
   * owner's current Finished records and always including {@code currentYear}.
   */
  Optional<FinishedShelf> findFinished(CurrentUser owner, Year selectedYear, Year currentYear);
}
