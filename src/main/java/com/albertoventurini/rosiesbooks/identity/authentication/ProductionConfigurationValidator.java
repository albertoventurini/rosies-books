package com.albertoventurini.rosiesbooks.identity.authentication;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Rejects missing and unsafe deployment configuration before the application accepts traffic. */
@Startup
@ApplicationScoped
@IfBuildProfile("prod")
class ProductionConfigurationValidator {

  ProductionConfigurationValidator(
      @ConfigProperty(name = "quarkus.datasource.jdbc.url") String databaseUrl,
      @ConfigProperty(name = "quarkus.datasource.username") String databaseUsername,
      @ConfigProperty(name = "quarkus.datasource.password") String databasePassword,
      @ConfigProperty(name = "quarkus.oidc.client-id") String oidcClientId,
      @ConfigProperty(name = "quarkus.oidc.credentials.secret") String oidcClientSecret,
      @ConfigProperty(name = "quarkus.oidc.authentication.state-secret") String oidcStateSecret,
      @ConfigProperty(name = "rosies-books.oidc.allowed-emails") String allowedEmails,
      @ConfigProperty(name = "rosies-books.review-token.secret") String reviewTokenSecret,
      @ConfigProperty(name = "rosies-books.open-library.operator-contact") String operatorContact,
      @ConfigProperty(name = "rosies-books.google-books.api-key") String googleBooksApiKey) {
    validate(
        Map.of(
            "database URL", databaseUrl,
            "database username", databaseUsername,
            "database password", databasePassword,
            "Google OIDC client ID", oidcClientId,
            "Google OIDC client secret", oidcClientSecret,
            "Google OIDC state secret", oidcStateSecret,
            "Google OIDC allowed emails", allowedEmails,
            "review-token secret", reviewTokenSecret,
            "Open Library operator contact", operatorContact,
            "Google Books API key", googleBooksApiKey));
  }

  static void validate(Map<String, String> values) {
    values.forEach(ProductionConfigurationValidator::validateValue);
  }

  private static void validateValue(String name, String value) {
    if (value == null || value.isBlank()) {
      throw invalid(name, "is required");
    }
    String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
    if (normalized.contains("replace-with")
        || normalized.contains("change-before-production")
        || normalized.equals("rosies-local")
        || normalized.equals("rosies")
        || normalized.contains("allowed-reader@example.com")
        || normalized.contains("local-tester@example.com")) {
      throw invalid(name, "must not use an example or development value");
    }
  }

  private static IllegalStateException invalid(String name, String reason) {
    return new IllegalStateException("Production configuration " + name + " " + reason);
  }
}
