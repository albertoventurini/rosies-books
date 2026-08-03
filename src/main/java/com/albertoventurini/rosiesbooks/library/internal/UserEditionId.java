package com.albertoventurini.rosiesbooks.library.internal;

import java.util.Objects;
import java.util.UUID;

/** Internal identity of one user's link to a canonical edition. */
public record UserEditionId(UUID value) {

  public UserEditionId {
    Objects.requireNonNull(value, "value");
  }
}
