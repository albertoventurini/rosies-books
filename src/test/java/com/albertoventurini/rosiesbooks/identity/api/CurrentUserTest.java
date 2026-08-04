package com.albertoventurini.rosiesbooks.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentUserTest {

  private static final UserId ID = new UserId(new UUID(1, 2));

  @Test
  void requiresAndNormalizesANonblankDisplayLabel() {
    assertEquals("Reader", new CurrentUser(ID, "  Reader  ").displayLabel());
    assertThrows(NullPointerException.class, () -> new CurrentUser(ID, null));
    assertThrows(IllegalArgumentException.class, () -> new CurrentUser(ID, " \t "));
  }
}
