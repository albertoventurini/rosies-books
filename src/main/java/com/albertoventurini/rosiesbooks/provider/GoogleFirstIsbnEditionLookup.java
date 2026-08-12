package com.albertoventurini.rosiesbooks.provider;

import com.albertoventurini.rosiesbooks.provider.api.Isbn13;
import com.albertoventurini.rosiesbooks.provider.api.IsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.api.IsbnLookupResult;
import com.albertoventurini.rosiesbooks.provider.googlebooks.GoogleBooksIsbnEditionLookup;
import com.albertoventurini.rosiesbooks.provider.openlibrary.OpenLibraryIsbnEditionLookup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Uses Open Library only when Google Books has no exact ISBN match. */
@ApplicationScoped
public class GoogleFirstIsbnEditionLookup implements IsbnEditionLookup {
  private final IsbnEditionLookup googleBooks;
  private final IsbnEditionLookup openLibrary;

  @Inject
  GoogleFirstIsbnEditionLookup(
      GoogleBooksIsbnEditionLookup googleBooks, OpenLibraryIsbnEditionLookup openLibrary) {
    this(googleBooks::lookup, openLibrary::lookup);
  }

  GoogleFirstIsbnEditionLookup(IsbnEditionLookup googleBooks, IsbnEditionLookup openLibrary) {
    this.googleBooks = googleBooks;
    this.openLibrary = openLibrary;
  }

  @Override
  public IsbnLookupResult lookup(Isbn13 isbn) {
    IsbnLookupResult result = googleBooks.lookup(isbn);
    return result instanceof IsbnLookupResult.NotFound ? openLibrary.lookup(isbn) : result;
  }
}
