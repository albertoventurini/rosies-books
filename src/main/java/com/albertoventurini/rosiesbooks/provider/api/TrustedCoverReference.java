package com.albertoventurini.rosiesbooks.provider.api;

import java.net.URI;
import java.util.Objects;

/** A trusted, constrained provider cover reference. */
public record TrustedCoverReference(URI value) {
  public TrustedCoverReference {
    Objects.requireNonNull(value, "value");
    if (!"https".equalsIgnoreCase(value.getScheme()) || !trustedHost(value.getHost()))
      throw new IllegalArgumentException("Cover reference is not trusted");
  }

  private static boolean trustedHost(String host) {
    return "covers.openlibrary.org".equalsIgnoreCase(host)
        || "books.google.com".equalsIgnoreCase(host);
  }
}
