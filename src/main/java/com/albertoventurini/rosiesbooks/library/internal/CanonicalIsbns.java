package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;
import java.util.Optional;

/** Canonical ISBN identifiers, with an ISBN-13 derived whenever ISBN-10 is supplied. */
public record CanonicalIsbns(Optional<Isbn10> isbn10, Optional<Isbn13> isbn13) {

  public CanonicalIsbns {
    Objects.requireNonNull(isbn10, "isbn10");
    Objects.requireNonNull(isbn13, "isbn13");
    if (isbn10.isPresent()) {
      Isbn13 derived = isbn10.orElseThrow().toIsbn13();
      if (isbn13.isPresent() && !isbn13.orElseThrow().equals(derived)) {
        throw new IllegalArgumentException("ISBN-10 and ISBN-13 identify different editions");
      }
      isbn13 = Optional.of(derived);
    }
  }

  public static CanonicalIsbns none() {
    return new CanonicalIsbns(Optional.empty(), Optional.empty());
  }
}
