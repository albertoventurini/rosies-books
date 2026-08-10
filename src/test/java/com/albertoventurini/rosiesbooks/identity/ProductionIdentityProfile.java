package com.albertoventurini.rosiesbooks.identity;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.List;
import java.util.Map;
import org.testcontainers.postgresql.PostgreSQLContainer;

public class ProductionIdentityProfile implements QuarkusTestProfile {

  @Override
  public String getConfigProfile() {
    return "prod";
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.oidc.enabled", "false",
        "quarkus.oidc.client-id", "production-test-client",
        "quarkus.oidc.credentials.secret", "production-test-secret",
        "quarkus.oidc.authentication.state-secret", "production-test-state-secret",
        "rosies-books.oidc.allowed-emails", "reader@example.com",
        "rosies-books.review-token.secret", "production-test-review-token-secret",
        "rosies-books.open-library.operator-contact", "production-test@invalid.example");
  }

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(ProductionDatabase.class));
  }

  public static final class ProductionDatabase implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer postgres;

    @Override
    public Map<String, String> start() {
      postgres =
          new PostgreSQLContainer("postgres:18.4")
              .withDatabaseName("production_identity")
              .withUsername("production_identity")
              .withPassword("production-identity-test-only");
      postgres.start();
      return Map.of(
          "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
          "quarkus.datasource.username", postgres.getUsername(),
          "quarkus.datasource.password", postgres.getPassword());
    }

    @Override
    public void stop() {
      if (postgres != null) {
        postgres.stop();
      }
    }
  }
}
