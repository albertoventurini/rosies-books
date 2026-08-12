package com.albertoventurini.rosiesbooks.provider.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class TrustedCoverReferenceTest {
  @Test
  void acceptsGoogleBooksAndOpenLibraryCoverHosts() {
    assertDoesNotThrow(
        () -> new TrustedCoverReference(URI.create("https://books.google.com/books?id=one")));
    assertDoesNotThrow(
        () -> new TrustedCoverReference(URI.create("https://covers.openlibrary.org/b/id/1-L.jpg")));
  }

  @Test
  void rejectsAnUntrustedHost() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TrustedCoverReference(URI.create("https://example.test/cover.jpg")));
  }
}
