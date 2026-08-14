package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.library.shelves.ShelfSearchResult;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

record SearchPage(
    String productName,
    List<ShelfNavigationItem> navigation,
    String query,
    String validationMessage,
    List<SearchShelfView> shelves) {

  SearchPage {
    navigation = List.copyOf(navigation);
    shelves = List.copyOf(shelves);
  }

  static SearchPage results(
      String query, List<ShelfSearchResult> results, LocalDate today, ZoneId zone) {
    return new SearchPage(
        "Rosie's books",
        inactiveNavigation(),
        query,
        null,
        results.stream()
            .map(
                result ->
                    new SearchShelfView(
                        result.shelf().heading(),
                        result.books().stream()
                            .map(book -> ShelfBookView.from(book, today, zone))
                            .toList()))
            .toList());
  }

  static SearchPage blank() {
    return new SearchPage("Rosie's books", inactiveNavigation(), "", null, List.of());
  }

  static SearchPage invalid(String submitted) {
    return new SearchPage(
        "Rosie's books",
        inactiveNavigation(),
        submitted == null ? "" : submitted.trim(),
        "Enter at least 3 letters, or at least 6 digits for an ISBN.",
        List.of());
  }

  public boolean hasValidationMessage() {
    return validationMessage != null;
  }

  public boolean hasResults() {
    return !shelves.isEmpty();
  }

  private static List<ShelfNavigationItem> inactiveNavigation() {
    return Arrays.stream(Shelf.values())
        .map(shelf -> new ShelfNavigationItem(shelf.route(), shelf.heading(), false))
        .toList();
  }
}

record SearchShelfView(String heading, List<ShelfBookView> books) {

  SearchShelfView {
    books = List.copyOf(books);
  }
}
