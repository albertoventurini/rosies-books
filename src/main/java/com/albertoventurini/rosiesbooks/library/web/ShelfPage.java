package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.FinishedShelf;
import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfBook;
import java.time.LocalDate;
import java.time.Year;
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
    String notice,
    FinishedYearView finishedYear) {

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
        notice,
        null);
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

  public LibraryChrome chrome() {
    return new LibraryChrome(productName, navigation);
  }

  static ShelfPage finished(
      String userDisplayLabel,
      FinishedShelf finished,
      String notice,
      LocalDate today,
      ZoneId zone) {
    List<ShelfBookView> books =
        finished.books().stream().map(book -> ShelfBookView.from(book, today, zone)).toList();
    Year selectedYear = finished.selectedYear();
    return new ShelfPage(
        "Rosie's books",
        userDisplayLabel,
        Shelf.FINISHED.heading(),
        "No books finished in " + selectedYear + ".",
        Arrays.stream(Shelf.values())
            .map(
                shelf ->
                    new ShelfNavigationItem(
                        shelf.route(), shelf.heading(), shelf == Shelf.FINISHED))
            .toList(),
        books,
        notice,
        new FinishedYearView(
            selectedYear.toString(),
            finished.availableYears().stream()
                .map(
                    year ->
                        new FinishedYearOption(
                            year.toString(), "/finished?year=" + year, year.equals(selectedYear)))
                .toList(),
            books.size()));
  }

  public boolean hasFinishedYear() {
    return finishedYear != null;
  }
}

record ShelfNavigationItem(String route, String label, boolean active) {}

record FinishedYearView(String selectedYear, List<FinishedYearOption> options, int bookCount) {

  FinishedYearView {
    options = List.copyOf(options);
  }

  public String countText() {
    return bookCount + (bookCount == 1 ? " book read in " : " books read in ") + selectedYear;
  }
}

record FinishedYearOption(String label, String route, boolean selected) {}

record ShelfBookView(
    String id,
    String title,
    String authorsText,
    BookPlaceholder placeholder,
    String contextLine,
    String stateUrl,
    String deleteUrl,
    String coverUrl) {

  static ShelfBookView from(ShelfBook book, LocalDate today, ZoneId zone) {
    BookPlaceholder placeholder = BookPlaceholder.from(book.title(), book.authors());
    String id = book.userEditionId().value().toString();
    return new ShelfBookView(
        id,
        book.title(),
        String.join(", ", book.authors()),
        placeholder,
        ShelfDatePresentation.contextLine(book.readingState(), book.createdAt(), today, zone),
        "/books/" + id + "/state",
        "/books/" + id + "/delete",
        book.coverHash() == null ? null : "/covers/" + book.coverHash());
  }

  public boolean hasCover() {
    return coverUrl != null;
  }
}
