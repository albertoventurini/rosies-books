CREATE TABLE cover_asset (
    id uuid NOT NULL,
    content bytea NOT NULL,
    mime_type text NOT NULL,
    CONSTRAINT cover_asset_pkey PRIMARY KEY (id)
);
