package com.albertoventurini.rosiesbooks.platform.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ReadingStateMigrationCompatibilityTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4")
          .withDatabaseName("reading_state_migration")
          .withUsername("migration_test")
          .withPassword("migration-test-only");

  @Test
  void refusesToMigrateAnExistingInvalidReadingStateWithoutRepairingIt() throws Exception {
    Flyway beforeStateConstraints = flyway("5");
    beforeStateConstraints.migrate();
    insertReadingWithoutAStartDate();

    FlywayException failure = assertThrows(FlywayException.class, () -> flyway(null).migrate());

    assertTrue(messageChain(failure).contains("user_edition_state_dates"));
    assertEquals("5", beforeStateConstraints.info().current().getVersion().getVersion());
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery("select state, started_on, finished_on from user_edition")) {
      assertTrue(rows.next());
      assertEquals("READING", rows.getString("state"));
      assertEquals(null, rows.getDate("started_on"));
      assertEquals(null, rows.getDate("finished_on"));
    }
  }

  private static Flyway flyway(String target) {
    var configuration =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration");
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }

  private static void insertReadingWithoutAStartDate() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          """
          insert into app_user (
            id, oidc_issuer, oidc_subject, email, created_at, updated_at)
          values (
            '10000000-0000-0000-0000-000000000001', 'https://issuer.example',
            'migration-user', 'migration@example.com',
            '2026-08-04 00:00:00+00', '2026-08-04 00:00:00+00');

          insert into edition (
            id, title, metadata_origin, created_at, updated_at)
          values (
            '20000000-0000-0000-0000-000000000001', 'Migration edition', 'MANUAL',
            '2026-08-04 00:00:00+00', '2026-08-04 00:00:00+00');

          insert into user_edition (
            id, user_id, edition_id, state, started_on, finished_on, private_notes,
            effective_title_search, effective_authors_search, created_at, updated_at)
          values (
            '30000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000001',
            '20000000-0000-0000-0000-000000000001',
            'READING', null, null, null, 'Migration edition', 'Migration author',
            '2026-08-04 00:00:00+00', '2026-08-04 00:00:00+00');
          """);
    }
  }

  private static String messageChain(Throwable failure) {
    StringBuilder messages = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      messages.append(cause.getMessage()).append('\n');
    }
    return messages.toString();
  }
}
