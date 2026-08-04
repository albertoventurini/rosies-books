package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfBook;
import java.util.Arrays;
import java.util.List;

record ShelfPage(
    String productName,
    String userDisplayLabel,
    String heading,
    String emptyMessage,
    List<ShelfNavigationItem> navigation,
    List<ShelfBookView> books,
    String notice) {

  ShelfPage {
    navigation = List.copyOf(navigation);
    books = List.copyOf(books);
  }

  static ShelfPage from(
      String userDisplayLabel, Shelf activeShelf, List<ShelfBook> books, String notice) {
    return new ShelfPage(
        "Rosie's books",
        userDisplayLabel,
        activeShelf.heading(),
        activeShelf.emptyMessage(),
        Arrays.stream(Shelf.values())
            .map(
                shelf ->
                    new ShelfNavigationItem(shelf.route(), shelf.heading(), shelf == activeShelf))
            .toList(),
        books.stream().map(ShelfBookView::from).toList(),
        notice);
  }

  static ShelfPage from(String userDisplayLabel, Shelf activeShelf, List<ShelfBook> books) {
    return from(userDisplayLabel, activeShelf, books, null);
  }

  public boolean hasNotice() {
    return notice != null;
  }
}

record ShelfNavigationItem(String route, String label, boolean active) {}

record ShelfBookView(String id, String title, String authorsText, BookPlaceholder placeholder) {

  static ShelfBookView from(ShelfBook book) {
    BookPlaceholder placeholder = BookPlaceholder.from(book.title(), book.authors());
    return new ShelfBookView(
        book.userEditionId().value().toString(),
        book.title(),
        String.join(", ", book.authors()),
        placeholder);
  }
}
