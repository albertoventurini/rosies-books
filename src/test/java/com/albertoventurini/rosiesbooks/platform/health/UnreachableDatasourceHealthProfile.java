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
    return Map.of(
        "quarkus.datasource.db-kind", "postgresql",
        "quarkus.datasource.devservices.enabled", "false",
        "quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:1/rosies",
        "quarkus.datasource.username", USERNAME,
        "quarkus.datasource.password", PASSWORD,
        "quarkus.datasource.jdbc.acquisition-timeout", "1S",
        "quarkus.flyway.migrate-at-start", "false");
  }
}
