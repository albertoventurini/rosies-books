package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import java.util.Arrays;
import java.util.List;

/** The common navigation shown around every library workflow. */
record LibraryChrome(String productName, List<ShelfNavigationItem> navigation) {
  LibraryChrome {
    navigation = List.copyOf(navigation);
  }

  static LibraryChrome forShelf(Shelf activeShelf) {
    return new LibraryChrome(
        "Rosie's books",
        Arrays.stream(Shelf.values())
            .map(
                shelf ->
                    new ShelfNavigationItem(shelf.route(), shelf.heading(), shelf == activeShelf))
            .toList());
  }

  static LibraryChrome inactive() {
    return forShelf(null);
  }
}
