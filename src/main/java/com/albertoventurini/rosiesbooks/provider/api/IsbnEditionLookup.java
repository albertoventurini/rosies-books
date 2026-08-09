package com.albertoventurini.rosiesbooks.provider.api;

/** Looks up one concrete edition by its normalized ISBN-13. */
public interface IsbnEditionLookup {
  IsbnLookupResult lookup(Isbn13 isbn);
}
