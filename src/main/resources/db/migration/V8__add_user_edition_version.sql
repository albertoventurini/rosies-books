ALTER TABLE user_edition
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT user_edition_version_nonnegative CHECK (version >= 0);
