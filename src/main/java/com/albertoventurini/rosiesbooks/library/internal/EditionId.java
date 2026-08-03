package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;
import java.util.UUID;

/** Internal canonical-edition identity. */
public record EditionId(UUID value) {

  public EditionId {
    Objects.requireNonNull(value, "value");
  }
}
