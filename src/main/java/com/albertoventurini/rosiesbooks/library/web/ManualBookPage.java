package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.library.shelves.Shelf;
import java.util.Arrays;
import java.util.List;

record ManualBookPage(
    String productName,
    String userDisplayLabel,
    List<ShelfNavigationItem> navigation,
    ManualBookForm form) {

  ManualBookPage(String userDisplayLabel, ManualBookForm form) {
    this(
        "Rosie's books",
        userDisplayLabel,
        Arrays.stream(Shelf.values())
            .map(shelf -> new ShelfNavigationItem(shelf.route(), shelf.heading(), false))
            .toList(),
        form);
  }
}

record ManualBookReviewPage(
    String productName,
    String userDisplayLabel,
    List<ShelfNavigationItem> navigation,
    ManualBookForm form,
    ManualBookReview review) {

  ManualBookReviewPage(String userDisplayLabel, ManualBookForm form, ManualBookReview review) {
    this(
        "Rosie's books",
        userDisplayLabel,
        Arrays.stream(Shelf.values())
            .map(shelf -> new ShelfNavigationItem(shelf.route(), shelf.heading(), false))
            .toList(),
        form,
        review);
  }
}
