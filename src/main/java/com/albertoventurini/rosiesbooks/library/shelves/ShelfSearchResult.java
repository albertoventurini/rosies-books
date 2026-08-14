package com.albertoventurini.rosiesbooks.library.shelves;

import java.util.List;

/** The matching books for one non-empty shelf. */
public record ShelfSearchResult(Shelf shelf, List<ShelfBook> books) {

  public ShelfSearchResult {
    books = List.copyOf(books);
    if (books.isEmpty()) {
      throw new IllegalArgumentException("Search results must not contain empty shelves");
    }
  }
}
