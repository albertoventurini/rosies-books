package com.albertoventurini.rosiesbooks.identity.api;

import java.util.Objects;

/** The authenticated application user required by every private-library operation. */
public record CurrentUser(UserId id, String displayLabel) {

  public CurrentUser {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(displayLabel, "displayLabel");
    displayLabel = displayLabel.strip();
    if (displayLabel.isBlank()) {
      throw new IllegalArgumentException("displayLabel must not be blank");
    }
  }
}
