package com.albertoventurini.rosiesbooks.platform.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DatabaseMigrationTest {

  @Inject Flyway flyway;

  @Test
  void appliesEveryMigrationInVersionOrderToTheFreshTestDatabase() {
    var appliedVersions =
        Arrays.stream(flyway.info().applied()).map(info -> info.getVersion().getVersion()).toList();

    assertEquals(java.util.List.of("1", "2", "3", "4", "5", "6"), appliedVersions);
    assertEquals("6", flyway.info().current().getVersion().getVersion());
  }
}
