package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.api.BookDeletionService.DeletionBook;

record BookDeletionPage(
    String productName,
    String userDisplayLabel,
    String bookId,
    String title,
    String currentShelf,
    String currentShelfRoute,
    long version,
    boolean conflict,
    String error) {

  static BookDeletionPage confirmation(String userLabel, DeletionBook book) {
    return create(userLabel, book, false, null);
  }

  static BookDeletionPage conflict(String userLabel, DeletionBook book) {
    return create(userLabel, book, true, null);
  }

  static BookDeletionPage invalid(String userLabel, DeletionBook book, String error) {
    return create(userLabel, book, false, error);
  }

  private static BookDeletionPage create(
      String userLabel, DeletionBook book, boolean conflict, String error) {
    return new BookDeletionPage(
        "Rosie's books",
        userLabel,
        book.id().toString(),
        book.title(),
        book.shelf().label(),
        book.shelf().route(),
        book.version(),
        conflict,
        error);
  }

  public boolean hasError() {
    return error != null;
  }
}
