ALTER TABLE cover_asset
    ADD CONSTRAINT cover_asset_content_max_5_mib
    CHECK (octet_length(content) <= 5242880);
