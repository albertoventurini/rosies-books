package com.albertoventurini.rosiesbooks.library.web;

import com.albertoventurini.rosiesbooks.provider.api.SelectedEdition;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

record ProviderAddBookPage(
    String userDisplayLabel, String submittedIsbn, String message, Optional<Result> result) {
  ProviderAddBookPage {
    submittedIsbn = submittedIsbn == null ? "" : submittedIsbn;
    result = result == null ? Optional.empty() : result;
  }

  static ProviderAddBookPage empty(String userDisplayLabel) {
    return new ProviderAddBookPage(userDisplayLabel, "", null, Optional.empty());
  }

  static ProviderAddBookPage error(String userDisplayLabel, String isbn, String message) {
    return new ProviderAddBookPage(userDisplayLabel, isbn, message, Optional.empty());
  }

  static ProviderAddBookPage found(
      String userDisplayLabel, String isbn, SelectedEdition edition, String token) {
    return new ProviderAddBookPage(
        userDisplayLabel, isbn, null, Optional.of(new Result(edition, token)));
  }

  public String manualFallbackRoute() {
    return "/books/new/manual?isbn=" + URLEncoder.encode(submittedIsbn, StandardCharsets.UTF_8);
  }

  record Result(SelectedEdition edition, String reviewToken) {}
}
