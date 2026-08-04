ALTER TABLE user_edition
    ADD COLUMN request_id uuid,
    ADD CONSTRAINT user_edition_user_request_key UNIQUE (user_id, request_id);
