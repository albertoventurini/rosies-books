package com.albertoventurini.rosiesbooks.identity.api;

import java.util.Objects;

/** The authenticated application user required by every private-library operation. */
public record CurrentUser(UserId id) {

  public CurrentUser {
    Objects.requireNonNull(id, "id");
  }
}
