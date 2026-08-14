package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

record ProviderAddBookPage(
    String productName,
    List<ShelfNavigationItem> navigation,
    String userDisplayLabel,
    String submittedIsbn,
    String message,
    Optional<Result> result) {
  ProviderAddBookPage {
    navigation = List.copyOf(navigation);
    submittedIsbn = submittedIsbn == null ? "" : submittedIsbn;
    result = result == null ? Optional.empty() : result;
  }

  ProviderAddBookPage(
      String userDisplayLabel, String submittedIsbn, String message, Optional<Result> result) {
    this(
        "Rosie's books",
        Arrays.stream(Shelf.values())
            .map(shelf -> new ShelfNavigationItem(shelf.route(), shelf.heading(), false))
            .toList(),
        userDisplayLabel,
        submittedIsbn,
        message,
        result);
  }

  static ProviderAddBookPage empty(String userDisplayLabel) {
    return new ProviderAddBookPage(userDisplayLabel, "", null, Optional.empty());
  }

  static ProviderAddBookPage error(String userDisplayLabel, String isbn, String message) {
    return new ProviderAddBookPage(userDisplayLabel, isbn, message, Optional.empty());
  }

  static ProviderAddBookPage found(
      String userDisplayLabel,
      String isbn,
      SelectedEdition edition,
      String token,
      ManualBookForm form) {
    return found(userDisplayLabel, isbn, edition, token, form, null);
  }

  static ProviderAddBookPage found(
      String userDisplayLabel,
      String isbn,
      SelectedEdition edition,
      String token,
      ManualBookForm form,
      String localCoverUrl) {
    return new ProviderAddBookPage(
        userDisplayLabel, isbn, null, Optional.of(new Result(edition, token, form, localCoverUrl)));
  }

  public String manualFallbackRoute() {
    return "/books/new/manual?isbn=" + URLEncoder.encode(submittedIsbn, StandardCharsets.UTF_8);
  }

  public LibraryChrome chrome() {
    return new LibraryChrome(productName, navigation);
  }

  record Result(
      SelectedEdition edition, String reviewToken, ManualBookForm form, String localCoverUrl) {
    public List<String> descriptionParagraphs() {
      return edition.description().stream()
          .flatMap(description -> Arrays.stream(description.split("\\R+")))
          .filter(paragraph -> !paragraph.isBlank())
          .toList();
    }
  }
}
