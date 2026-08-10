package com.albertoventurini.rosiesbooks.identity.authentication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionConfigurationValidatorTest {

  @Test
  void accepts_complete_nonexample_configuration() {
    assertDoesNotThrow(() -> ProductionConfigurationValidator.validate(validValues()));
  }

  @Test
  void rejects_missing_or_blank_values() {
    assertThrows(
        IllegalStateException.class,
        () -> ProductionConfigurationValidator.validate(Map.of("database password", " ")));
  }

  @Test
  void rejects_example_values() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionConfigurationValidator.validate(
                Map.of("Google OIDC client secret", "replace-with-secret-manager-value")));
  }

  @Test
  void rejects_known_development_values() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ProductionConfigurationValidator.validate(
                Map.of(
                    "review-token secret",
                    "development-review-token-secret-change-before-production")));
  }

  private static Map<String, String> validValues() {
    return Map.of(
        "database URL", "jdbc:postgresql://postgres:5432/rosies_books",
        "database username", "rosies_books_app",
        "database password", "a-long-unique-database-password",
        "Google OIDC client ID", "1234567890-example.apps.googleusercontent.com",
        "Google OIDC client secret", "a-long-unique-google-client-secret",
        "Google OIDC state secret", "a-long-unique-state-secret",
        "Google OIDC allowed emails", "reader@private-domain.invalid",
        "review-token secret", "a-long-unique-review-token-secret",
        "Open Library operator contact", "operator@private-domain.invalid");
  }
}
