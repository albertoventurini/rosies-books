package com.albertoventurini.rosiesbooks.provider;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GoogleFirstIsbnEditionLookupTest {
  private static final Isbn13 ISBN = new Isbn13("9780306406157");

  @Test
  void usesOpenLibraryOnlyWhenGoogleBooksHasNoMatchingVolume() {
    AtomicInteger openLibraryCalls = new AtomicInteger();
    IsbnLookupResult expected = new IsbnLookupResult.Unavailable();
    IsbnEditionLookup lookup =
        new GoogleFirstIsbnEditionLookup(
            ignored -> new IsbnLookupResult.NotFound(),
            ignored -> {
              openLibraryCalls.incrementAndGet();
              return expected;
            });

    assertSame(expected, lookup.lookup(ISBN));
    org.junit.jupiter.api.Assertions.assertEquals(1, openLibraryCalls.get());
  }

  @Test
  void retainsAGoogleBooksFailureWithoutCallingOpenLibrary() {
    AtomicInteger openLibraryCalls = new AtomicInteger();
    IsbnEditionLookup lookup =
        new GoogleFirstIsbnEditionLookup(
            ignored -> new IsbnLookupResult.RateLimited(java.util.Optional.empty()),
            ignored -> {
              openLibraryCalls.incrementAndGet();
              return new IsbnLookupResult.NotFound();
            });

    assertInstanceOf(IsbnLookupResult.RateLimited.class, lookup.lookup(ISBN));
    org.junit.jupiter.api.Assertions.assertEquals(0, openLibraryCalls.get());
  }
}
