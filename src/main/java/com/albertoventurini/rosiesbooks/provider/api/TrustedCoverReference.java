package com.albertoventurini.rosiesbooks.provider.api;

import java.net.URI;
import java.util.Objects;

/** A trusted, constrained provider cover reference. */
public record TrustedCoverReference(URI value) {
  public TrustedCoverReference {
    Objects.requireNonNull(value, "value");
    if (!"https".equals(value.getScheme()) || !"covers.openlibrary.org".equals(value.getHost()))
      throw new IllegalArgumentException("Cover reference is not trusted");
  }
}
