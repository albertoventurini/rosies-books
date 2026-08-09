package com.albertoventurini.rosiesbooks.identity.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleIdentityValidatorTest {

  private final GoogleIdentityValidator validator =
      new GoogleIdentityValidator(List.of(" Reader@Example.com "));

  @Test
  void acceptsOnlyAnAllowedVerifiedGoogleIdentityAndNormalizesItsBoundaryValues() {
    GoogleIdentity identity =
        validator
            .validate(
                " https://accounts.google.com ", " subject-1 ", " READER@example.COM ", true)
            .orElseThrow();

    assertEquals("https://accounts.google.com", identity.issuer());
    assertEquals("subject-1", identity.subject());
    assertEquals("reader@example.com", identity.email());
  }

  @Test
  void rejectsEachInvalidOrUnapprovedClaimShape() {
    for (Object[] claims :
        List.of(
            new Object[] {"https://other.example", "subject", "reader@example.com", true},
            new Object[] {"https://accounts.google.com", " ", "reader@example.com", true},
            new Object[] {"https://accounts.google.com", "subject", null, true},
            new Object[] {"https://accounts.google.com", "subject", "not-an-email", true},
            new Object[] {"https://accounts.google.com", "subject", "reader@example.com", false},
            new Object[] {"https://accounts.google.com", "subject", "reader@example.com", "true"},
            new Object[] {"https://accounts.google.com", "subject", "other@example.com", true})) {
      assertFalse(validator.validate(claims[0], claims[1], claims[2], claims[3]).isPresent());
    }
  }

  @Test
  void refusesAnEmptyOrMalformedAllowlistAtStartup() {
    assertThrows(IllegalStateException.class, () -> new GoogleIdentityValidator(List.of()));
    assertThrows(
        IllegalStateException.class, () -> new GoogleIdentityValidator(List.of("not-an-email")));
  }
}
