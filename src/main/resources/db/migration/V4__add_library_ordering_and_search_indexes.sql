-- [jooq ignore start]
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX user_edition_reading_order_idx
    ON user_edition (user_id, started_on DESC, id)
    WHERE state = 'READING';

CREATE INDEX user_edition_to_read_order_idx
    ON user_edition (user_id, created_at DESC, id)
    WHERE state = 'TO_READ';

CREATE INDEX user_edition_finished_order_idx
    ON user_edition (user_id, finished_on DESC, id)
    WHERE state = 'FINISHED';

CREATE INDEX user_edition_title_search_trgm_idx
    ON user_edition USING gin (lower(effective_title_search) gin_trgm_ops);

CREATE INDEX user_edition_authors_search_trgm_idx
    ON user_edition USING gin (lower(effective_authors_search) gin_trgm_ops);
-- [jooq ignore stop]
