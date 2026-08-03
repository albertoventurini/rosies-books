package com.albertoventurini.rosiesbooks.identity.api;

import java.util.Objects;
import java.util.UUID;

/** Stable application identity passed to every owner-scoped library operation. */
public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "value");
  }
}
