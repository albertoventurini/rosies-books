package com.albertoventurini.rosiesbooks.library.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jooq.DSLContext;

@ApplicationScoped
class IndexPlanTestCoordinator {

  private final DSLContext dsl;

  IndexPlanTestCoordinator(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Transactional
  String explainShelf(String state, String orderColumn) {
    dsl.execute("set local enable_seqscan = off");
    return plan(
        "explain (costs off) select id from user_edition where user_id = '"
            + UUID.randomUUID()
            + "'::uuid and state = '"
            + state
            + "' order by "
            + orderColumn
            + " desc, id");
  }

  @Transactional
  String explainSearch(String column) {
    UUID owner = UUID.randomUUID();
    createSearchFixture(owner);
    dsl.execute("set local enable_seqscan = off");
    return plan(
        "explain (costs off) select id from user_edition where lower("
            + column
            + ") like '%needle%'");
  }

  private void createSearchFixture(UUID owner) {
    dsl.execute(
        "insert into app_user"
            + " (id, oidc_issuer, oidc_subject, email, created_at, updated_at) values ('"
            + owner
            + "'::uuid, 'index-test', '"
            + owner
            + "', 'index@example.com', '2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z')");
    dsl.execute(
        "insert into edition"
            + " (id, title, metadata_origin, created_at, updated_at)"
            + " select gen_random_uuid(), case when number = 1 then 'Needle volume'"
            + " else 'Haystack volume ' || number end, 'MANUAL',"
            + " '2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z'"
            + " from generate_series(1, 1000) number");
    dsl.execute(
        "insert into user_edition"
            + " (id, user_id, edition_id, state, effective_title_search,"
            + " effective_authors_search, created_at, updated_at)"
            + " select gen_random_uuid(), '"
            + owner
            + "'::uuid, id, 'TO_READ', title,"
            + " case when title = 'Needle volume' then 'Needle author' else 'Haystack author' end,"
            + " '2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z' from edition"
            + " where id not in (select edition_id from user_edition)");
    dsl.execute("analyze user_edition");
  }

  private String plan(String sql) {
    return String.join("\n", dsl.fetch(sql).getValues(0, String.class));
  }
}
