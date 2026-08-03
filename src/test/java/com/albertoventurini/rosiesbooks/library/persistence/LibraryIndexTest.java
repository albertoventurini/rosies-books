package com.albertoventurini.rosiesbooks.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LibraryIndexTest {

  private static final Set<String> INDEXES =
      Set.of(
          "user_edition_reading_order_idx",
          "user_edition_to_read_order_idx",
          "user_edition_finished_order_idx",
          "user_edition_title_search_trgm_idx",
          "user_edition_authors_search_trgm_idx");

  @Inject DSLContext dsl;
  @Inject IndexPlanTestCoordinator plans;

  @Test
  void catalogsContainEveryShelfAndPrivateSearchIndex() {
    assertEquals(
        INDEXES,
        Set.copyOf(
            dsl
                .fetch(
                    """
                    select indexname
                      from pg_indexes
                     where schemaname = 'public'
                    """)
                .getValues("indexname", String.class)
                .stream()
                .filter(INDEXES::contains)
                .toList()));
    assertEquals(
        List.of("pg_trgm"),
        dsl.fetch("select extname from pg_extension where extname = 'pg_trgm'")
            .getValues("extname", String.class));
  }

  @Test
  void representativeShelfAndPartialSearchShapesCanUseTheirIndexes() {
    assertTrue(
        plans.explainShelf("READING", "started_on").contains("user_edition_reading_order_idx"));
    assertTrue(
        plans.explainShelf("TO_READ", "created_at").contains("user_edition_to_read_order_idx"));
    assertTrue(
        plans.explainShelf("FINISHED", "finished_on").contains("user_edition_finished_order_idx"));
    assertTrue(
        plans
            .explainSearch("effective_title_search")
            .contains("user_edition_title_search_trgm_idx"));
    assertTrue(
        plans
            .explainSearch("effective_authors_search")
            .contains("user_edition_authors_search_trgm_idx"));
  }
}
