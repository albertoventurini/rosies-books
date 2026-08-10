package com.albertoventurini.rosiesbooks.platform.health;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class UnreachableDatasourceHealthProfile implements QuarkusTestProfile {

  static final String USERNAME = "health-private-user";
  static final String PASSWORD = "health-private-password";

  @Override
  public String getConfigProfile() {
    return "prod";
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.ofEntries(
        Map.entry("quarkus.oidc.enabled", "false"),
        Map.entry("quarkus.oidc.client-id", "health-test-client"),
        Map.entry("quarkus.oidc.credentials.secret", "health-test-client-secret"),
        Map.entry("quarkus.oidc.authentication.state-secret", "health-test-state-secret"),
        Map.entry("rosies-books.oidc.allowed-emails", "reader@example.com"),
        Map.entry("rosies-books.review-token.secret", "health-test-review-token-secret"),
        Map.entry("rosies-books.open-library.operator-contact", "health-test@invalid.example"),
        Map.entry("quarkus.datasource.db-kind", "postgresql"),
        Map.entry("quarkus.datasource.devservices.enabled", "false"),
        Map.entry("quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:1/rosies"),
        Map.entry("quarkus.datasource.username", USERNAME),
        Map.entry("quarkus.datasource.password", PASSWORD),
        Map.entry("quarkus.datasource.jdbc.acquisition-timeout", "1S"),
        Map.entry("quarkus.flyway.migrate-at-start", "false"));
  }
}
