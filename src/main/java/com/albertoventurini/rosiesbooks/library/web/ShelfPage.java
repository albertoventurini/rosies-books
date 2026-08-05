package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfBook;
import java.time.LocalDate;
import java.time.ZoneId;
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
      String userDisplayLabel,
      Shelf activeShelf,
      List<ShelfBook> books,
      String notice,
      LocalDate today,
      ZoneId zone) {
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
        books.stream().map(book -> ShelfBookView.from(book, today, zone)).toList(),
        notice);
  }

  static ShelfPage from(
      String userDisplayLabel,
      Shelf activeShelf,
      List<ShelfBook> books,
      LocalDate today,
      ZoneId zone) {
    return from(userDisplayLabel, activeShelf, books, null, today, zone);
  }

  public boolean hasNotice() {
    return notice != null;
  }
}

record ShelfNavigationItem(String route, String label, boolean active) {}

record ShelfBookView(
    String id,
    String title,
    String authorsText,
    BookPlaceholder placeholder,
    String stateLabel,
    String contextLine,
    String stateUrl,
    String deleteUrl) {

  static ShelfBookView from(ShelfBook book, LocalDate today, ZoneId zone) {
    BookPlaceholder placeholder = BookPlaceholder.from(book.title(), book.authors());
    String id = book.userEditionId().value().toString();
    return new ShelfBookView(
        id,
        book.title(),
        String.join(", ", book.authors()),
        placeholder,
        ShelfDatePresentation.stateLabel(book.readingState()),
        ShelfDatePresentation.contextLine(book.readingState(), book.createdAt(), today, zone),
        "/books/" + id + "/state",
        "/books/" + id + "/delete");
  }
}
