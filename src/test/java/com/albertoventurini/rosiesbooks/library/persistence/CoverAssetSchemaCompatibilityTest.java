package com.albertoventurini.rosiesbooks.library.persistence;

import static com.albertoventurini.rosiesbooks.library.persistence.jooq.Tables.COVER_ASSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoverAssetSchemaCompatibilityTest {

  @Inject DSLContext dsl;

  @Test
  void generatedModelAndMigratedPostgresSchemaDescribeTheSameTable() {
    Map<String, ColumnContract> generatedColumns = new LinkedHashMap<>();
    for (Field<?> field : COVER_ASSET.fields()) {
      generatedColumns.put(
          field.getName(),
          new ColumnContract(field.getDataType().getType(), field.getDataType().nullable()));
    }

    assertEquals(
        Map.of(
            "id", new ColumnContract(UUID.class, false),
            "content", new ColumnContract(byte[].class, false),
            "mime_type", new ColumnContract(String.class, false),
            "sha256", new ColumnContract(String.class, true),
            "width", new ColumnContract(Integer.class, true),
            "height", new ColumnContract(Integer.class, true),
            "provenance_url", new ColumnContract(String.class, true),
            "fetched_at", new ColumnContract(OffsetDateTime.class, true)),
        generatedColumns);
    assertEquals("cover_asset_pkey", COVER_ASSET.getPrimaryKey().getName());
    assertEquals(
        List.of("id"),
        COVER_ASSET.getPrimaryKey().getFields().stream().map(Field::getName).toList());
    assertEquals(
        List.of(
            "cover_asset_content_max_5_mib",
            "cover_asset_dimensions_positive",
            "cover_asset_sha256_format"),
        COVER_ASSET.getChecks().stream().map(check -> check.getName()).toList());

    var liveColumns =
        dsl.fetch(
                """
                select column_name, data_type, is_nullable
                  from information_schema.columns
                 where table_schema = 'public' and table_name = 'cover_asset'
                 order by ordinal_position
                """)
            .intoMaps();
    assertEquals(
        List.of(
            Map.of("column_name", "id", "data_type", "uuid", "is_nullable", "NO"),
            Map.of("column_name", "content", "data_type", "bytea", "is_nullable", "NO"),
            Map.of("column_name", "mime_type", "data_type", "text", "is_nullable", "NO"),
            Map.of("column_name", "sha256", "data_type", "character varying", "is_nullable", "YES"),
            Map.of("column_name", "width", "data_type", "integer", "is_nullable", "YES"),
            Map.of("column_name", "height", "data_type", "integer", "is_nullable", "YES"),
            Map.of("column_name", "provenance_url", "data_type", "text", "is_nullable", "YES"),
            Map.of(
                "column_name",
                "fetched_at",
                "data_type",
                "timestamp with time zone",
                "is_nullable",
                "YES")),
        liveColumns);

    assertEquals(
        List.of("id"),
        dsl.fetch(
                """
                select kcu.column_name
                  from information_schema.table_constraints tc
                  join information_schema.key_column_usage kcu
                    on tc.constraint_catalog = kcu.constraint_catalog
                   and tc.constraint_schema = kcu.constraint_schema
                   and tc.constraint_name = kcu.constraint_name
                 where tc.table_schema = 'public'
                   and tc.table_name = 'cover_asset'
                   and tc.constraint_type = 'PRIMARY KEY'
                 order by kcu.ordinal_position
                """)
            .getValues("column_name", String.class));

    String sizeConstraint =
        dsl.fetchOne(
                """
                select pg_get_constraintdef(c.oid)
                  from pg_constraint c
                  join pg_class t on t.oid = c.conrelid
                  join pg_namespace n on n.oid = t.relnamespace
                 where n.nspname = 'public'
                   and t.relname = 'cover_asset'
                   and c.conname = 'cover_asset_content_max_5_mib'
                """)
            .get(0, String.class);
    assertNotNull(sizeConstraint);
    assertTrue(sizeConstraint.contains("octet_length(content)"));
    assertTrue(sizeConstraint.contains("5242880"));
    assertFalse(sizeConstraint.isBlank());
  }

  private record ColumnContract(Class<?> javaType, boolean nullable) {}
}
