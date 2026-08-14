package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_FETCH_TASK;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.EDITION_AUTHOR;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.GOODREADS_IMPORT;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_AUTHOR_OVERRIDE;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_EDITION_METADATA_OVERRIDE;
import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.USER_PREFERENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.albertoventurini.rosiesbooks.identity.persistence.jooq.Tables;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoreOwnershipSchemaTest {

  @Inject DSLContext dsl;

  @Test
  void featureOwnedGeneratedTablesMatchLiveColumnTypesAndNullability() {
    Map<String, Table<?>> generated =
        Map.of(
            "app_user", Tables.APP_USER,
            "cover_asset", COVER_ASSET,
            "user_preference", USER_PREFERENCE,
            "edition", EDITION,
            "edition_author", EDITION_AUTHOR,
            "user_edition", USER_EDITION,
            "user_edition_metadata_override", USER_EDITION_METADATA_OVERRIDE,
            "user_edition_author_override", USER_EDITION_AUTHOR_OVERRIDE,
            "goodreads_import", GOODREADS_IMPORT,
            "cover_fetch_task", COVER_FETCH_TASK);

    for (var entry : generated.entrySet()) {
      Map<String, ColumnShape> generatedColumns = new LinkedHashMap<>();
      for (Field<?> field : entry.getValue().fields()) {
        generatedColumns.put(
            field.getName(),
            new ColumnShape(javaKind(field.getType()), field.getDataType().nullable()));
      }
      Map<String, ColumnShape> liveColumns = new LinkedHashMap<>();
      dsl.fetch(
              """
              select column_name, data_type, is_nullable
                from information_schema.columns
               where table_schema = 'public' and table_name = ?
               order by ordinal_position
              """,
              entry.getKey())
          .forEach(
              row ->
                  liveColumns.put(
                      row.get("column_name", String.class),
                      new ColumnShape(
                          databaseKind(row.get("data_type", String.class)),
                          "YES".equals(row.get("is_nullable", String.class)))));
      assertEquals(liveColumns, generatedColumns, entry.getKey());
    }

    assertTrue(Tables.APP_USER.getClass().getPackageName().contains("identity.persistence.jooq"));
    assertTrue(EDITION.getClass().getPackageName().contains("library.persistence.jooq"));
  }

  @Test
  void primaryUniqueCheckAndDeletionContractsAreNamedAndLive() {
    assertEquals(
        Map.of(
            "app_user", "app_user_pkey",
            "cover_asset", "cover_asset_pkey",
            "user_preference", "user_preference_pkey",
            "edition", "edition_pkey",
            "edition_author", "edition_author_pkey",
            "user_edition", "user_edition_pkey",
            "user_edition_metadata_override", "user_edition_metadata_override_pkey",
            "user_edition_author_override", "user_edition_author_override_pkey",
            "goodreads_import", "goodreads_import_pkey",
            "cover_fetch_task", "cover_fetch_task_pkey"),
        dsl.fetch(
                """
                select tc.table_name, tc.constraint_name
                  from information_schema.table_constraints tc
                 where tc.table_schema = 'public'
                   and tc.constraint_type = 'PRIMARY KEY'
                   and tc.table_name <> 'flyway_schema_history'
                """)
            .intoMap("table_name", "constraint_name"));

    assertEquals(
        Map.ofEntries(
            Map.entry("user_preference_user_fkey", "CASCADE"),
            Map.entry("edition_cover_asset_fkey", "SET NULL"),
            Map.entry("edition_author_edition_fkey", "CASCADE"),
            Map.entry("user_edition_user_fkey", "CASCADE"),
            Map.entry("user_edition_edition_fkey", "RESTRICT"),
            Map.entry("user_edition_metadata_override_user_edition_fkey", "CASCADE"),
            Map.entry("user_edition_author_override_metadata_fkey", "CASCADE"),
            Map.entry("goodreads_import_user_fkey", "CASCADE"),
            Map.entry("cover_fetch_task_user_fkey", "CASCADE"),
            Map.entry("cover_fetch_task_user_edition_fkey", "CASCADE"),
            Map.entry("cover_fetch_task_import_fkey", "CASCADE")),
        dsl.fetch(
                """
                select constraint_name, delete_rule
                  from information_schema.referential_constraints
                 where constraint_schema = 'public'
                """)
            .intoMap("constraint_name", "delete_rule"));

    Set<String> uniqueConstraints =
        Set.copyOf(
            dsl.fetch(
                    """
                    select constraint_name
                      from information_schema.table_constraints
                     where table_schema = 'public' and constraint_type = 'UNIQUE'
                    """)
                .getValues("constraint_name", String.class));
    assertTrue(
        uniqueConstraints.containsAll(
            Set.of(
                "app_user_oidc_identity_key",
                "edition_isbn13_key",
                "edition_provider_identity_key",
                "user_edition_user_edition_key",
                "user_edition_user_request_key")));

    Set<String> checks =
        Set.copyOf(
            dsl.fetch(
                    """
                    select constraint_name
                      from information_schema.table_constraints
                     where table_schema = 'public' and constraint_type = 'CHECK'
                    """)
                .getValues("constraint_name", String.class));
    assertTrue(
        checks.containsAll(
            Set.of(
                "edition_publication_components",
                "edition_provider_identity_pair",
                "edition_isbn_10_format",
                "edition_isbn_13_format",
                "edition_isbn_10_checksum",
                "edition_isbn_13_checksum",
                "edition_isbn_pair_consistent",
                "user_edition_state_check",
                "user_edition_state_dates",
                "user_edition_date_chronology",
                "user_edition_version_nonnegative",
                "user_edition_metadata_override_publication_date_value",
                "user_edition_metadata_override_isbn_10_checksum",
                "user_edition_metadata_override_isbn_13_checksum",
                "user_edition_metadata_override_title_value",
                "goodreads_import_counts_nonnegative",
                "cover_fetch_task_status_check",
                "cover_fetch_task_attempt_count_nonnegative")));
  }

  private static String javaKind(Class<?> type) {
    if (type == UUID.class) return "uuid";
    if (type == String.class) return "string";
    if (type == OffsetDateTime.class) return "instant";
    if (type == Integer.class) return "integer";
    if (type == Long.class) return "bigint";
    if (type == Boolean.class) return "boolean";
    if (type == LocalDate.class) return "date";
    if (type == byte[].class) return "bytes";
    throw new AssertionError("Unexpected generated type " + type);
  }

  private static String databaseKind(String type) {
    return switch (type) {
      case "uuid" -> "uuid";
      case "text", "character varying" -> "string";
      case "timestamp with time zone" -> "instant";
      case "integer" -> "integer";
      case "bigint" -> "bigint";
      case "boolean" -> "boolean";
      case "date" -> "date";
      case "bytea" -> "bytes";
      default -> throw new AssertionError("Unexpected live type " + type);
    };
  }

  private record ColumnShape(String kind, boolean nullable) {}
}
