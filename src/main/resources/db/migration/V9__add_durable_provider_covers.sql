ALTER TABLE cover_asset
    ADD COLUMN sha256 varchar(64),
    ADD COLUMN width integer,
    ADD COLUMN height integer,
    ADD COLUMN provenance_url text,
    ADD COLUMN fetched_at timestamp with time zone;

ALTER TABLE cover_asset
    ADD CONSTRAINT cover_asset_sha256_format CHECK
        (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT cover_asset_dimensions_positive CHECK
        ((width IS NULL AND height IS NULL) OR (width > 0 AND height > 0));

CREATE UNIQUE INDEX cover_asset_sha256_key ON cover_asset (sha256) WHERE sha256 IS NOT NULL;

ALTER TABLE edition
    ADD COLUMN trusted_cover_source text,
    ADD COLUMN cover_last_outcome varchar(32),
    ADD COLUMN cover_last_attempted_at timestamp with time zone;

ALTER TABLE edition
    ADD CONSTRAINT edition_cover_last_outcome_check CHECK
        (cover_last_outcome IS NULL OR cover_last_outcome IN ('SUCCESS', 'FAILED'));
