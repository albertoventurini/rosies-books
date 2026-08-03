CREATE TABLE app_user (
    id uuid NOT NULL,
    oidc_issuer varchar(2048) NOT NULL,
    oidc_subject varchar(2048) NOT NULL,
    email text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT app_user_pkey PRIMARY KEY (id),
    CONSTRAINT app_user_oidc_issuer_nonblank CHECK (char_length(trim(oidc_issuer)) > 0),
    CONSTRAINT app_user_oidc_subject_nonblank CHECK (char_length(trim(oidc_subject)) > 0),
    CONSTRAINT app_user_email_normalized CHECK (
        email = lower(trim(email)) AND char_length(email) > 0),
    CONSTRAINT app_user_oidc_identity_key UNIQUE (oidc_issuer, oidc_subject)
);

CREATE TABLE user_preference (
    user_id uuid NOT NULL,
    layout text NOT NULL,
    CONSTRAINT user_preference_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_preference_user_fkey FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_preference_layout_check CHECK (layout IN ('COVER_CARD', 'COMPACT_LIST'))
);

CREATE TABLE edition (
    id uuid NOT NULL,
    isbn_10 varchar(10),
    isbn_13 varchar(13),
    provider_name varchar(255),
    provider_edition_id varchar(2048),
    title text NOT NULL,
    subtitle text,
    format text,
    publisher text,
    publication_year integer,
    publication_month integer,
    publication_day integer,
    page_count integer,
    language text,
    description text,
    cover_asset_id uuid,
    metadata_origin text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT edition_pkey PRIMARY KEY (id),
    CONSTRAINT edition_cover_asset_fkey FOREIGN KEY (cover_asset_id)
        REFERENCES cover_asset (id) ON DELETE SET NULL,
    CONSTRAINT edition_isbn_10_format CHECK (
        isbn_10 IS NULL OR (char_length(isbn_10) = 10
            AND char_length(translate(isbn_10, '0123456789X', '')) = 0
            AND position('X' IN isbn_10) IN (0, 10))),
    CONSTRAINT edition_isbn_13_format CHECK (
        isbn_13 IS NULL OR (char_length(isbn_13) = 13
            AND char_length(translate(isbn_13, '0123456789', '')) = 0)),
    CONSTRAINT edition_provider_identity_pair CHECK (
        (provider_name IS NULL AND provider_edition_id IS NULL)
        OR (provider_name IS NOT NULL AND provider_edition_id IS NOT NULL
            AND provider_name = lower(trim(provider_name)) AND char_length(provider_name) > 0
            AND provider_edition_id = trim(provider_edition_id)
            AND char_length(provider_edition_id) > 0)),
    CONSTRAINT edition_title_nonblank CHECK (char_length(trim(title)) > 0),
    CONSTRAINT edition_page_count_positive CHECK (page_count IS NULL OR page_count > 0),
    CONSTRAINT edition_publication_components CHECK (
        (publication_year IS NULL AND publication_month IS NULL AND publication_day IS NULL)
        OR (publication_year IS NOT NULL AND publication_year BETWEEN 1 AND 9999
            AND (publication_month IS NULL OR publication_month BETWEEN 1 AND 12)
            AND (publication_month IS NOT NULL OR publication_day IS NULL)
            AND (publication_day IS NULL OR publication_day BETWEEN 1 AND
                CASE publication_month
                    WHEN 2 THEN CASE
                        WHEN publication_year % 400 = 0
                            OR (publication_year % 4 = 0 AND publication_year % 100 <> 0)
                        THEN 29 ELSE 28 END
                    WHEN 4 THEN 30 WHEN 6 THEN 30 WHEN 9 THEN 30 WHEN 11 THEN 30
                    ELSE 31
                END))),
    CONSTRAINT edition_metadata_origin_check CHECK (metadata_origin IN ('MANUAL', 'PROVIDER')),
    CONSTRAINT edition_isbn13_key UNIQUE (isbn_13),
    CONSTRAINT edition_provider_identity_key UNIQUE (provider_name, provider_edition_id)
);

CREATE TABLE edition_author (
    edition_id uuid NOT NULL,
    position integer NOT NULL,
    name text NOT NULL,
    CONSTRAINT edition_author_pkey PRIMARY KEY (edition_id, position),
    CONSTRAINT edition_author_edition_fkey FOREIGN KEY (edition_id)
        REFERENCES edition (id) ON DELETE CASCADE,
    CONSTRAINT edition_author_position_nonnegative CHECK (position >= 0),
    CONSTRAINT edition_author_name_nonblank CHECK (char_length(trim(name)) > 0)
);

CREATE TABLE user_edition (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    state text NOT NULL,
    started_on date,
    finished_on date,
    private_notes text,
    effective_title_search text NOT NULL,
    effective_authors_search text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT user_edition_pkey PRIMARY KEY (id),
    CONSTRAINT user_edition_user_fkey FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT user_edition_edition_fkey FOREIGN KEY (edition_id)
        REFERENCES edition (id) ON DELETE RESTRICT,
    CONSTRAINT user_edition_state_check CHECK (state IN ('TO_READ', 'READING', 'FINISHED')),
    CONSTRAINT user_edition_effective_title_nonblank CHECK (
        char_length(trim(effective_title_search)) > 0),
    CONSTRAINT user_edition_effective_authors_nonblank CHECK (
        char_length(trim(effective_authors_search)) > 0),
    CONSTRAINT user_edition_user_edition_key UNIQUE (user_id, edition_id)
);

CREATE TABLE user_edition_metadata_override (
    user_edition_id uuid NOT NULL,
    title_is_overridden boolean NOT NULL,
    title_value text,
    subtitle_is_overridden boolean NOT NULL,
    subtitle_value text,
    authors_is_overridden boolean NOT NULL,
    format_is_overridden boolean NOT NULL,
    format_value text,
    isbn_10_is_overridden boolean NOT NULL,
    isbn_10_value text,
    isbn_13_is_overridden boolean NOT NULL,
    isbn_13_value text,
    publisher_is_overridden boolean NOT NULL,
    publisher_value text,
    publication_date_is_overridden boolean NOT NULL,
    publication_year_value integer,
    publication_month_value integer,
    publication_day_value integer,
    page_count_is_overridden boolean NOT NULL,
    page_count_value integer,
    language_is_overridden boolean NOT NULL,
    language_value text,
    description_is_overridden boolean NOT NULL,
    description_value text,
    CONSTRAINT user_edition_metadata_override_pkey PRIMARY KEY (user_edition_id),
    CONSTRAINT user_edition_metadata_override_user_edition_fkey FOREIGN KEY (user_edition_id)
        REFERENCES user_edition (id) ON DELETE CASCADE,
    CONSTRAINT user_edition_metadata_override_authors_key
        UNIQUE (user_edition_id, authors_is_overridden),
    CONSTRAINT user_edition_metadata_override_title_value CHECK
        (title_is_overridden OR title_value IS NULL),
    CONSTRAINT user_edition_metadata_override_subtitle_value CHECK
        (subtitle_is_overridden OR subtitle_value IS NULL),
    CONSTRAINT user_edition_metadata_override_format_value CHECK
        (format_is_overridden OR format_value IS NULL),
    CONSTRAINT user_edition_metadata_override_isbn_10_value CHECK
        ((isbn_10_is_overridden OR isbn_10_value IS NULL)
         AND (isbn_10_value IS NULL OR (char_length(isbn_10_value) = 10
             AND char_length(translate(isbn_10_value, '0123456789X', '')) = 0
             AND position('X' IN isbn_10_value) IN (0, 10)))),
    CONSTRAINT user_edition_metadata_override_isbn_13_value CHECK
        ((isbn_13_is_overridden OR isbn_13_value IS NULL)
         AND (isbn_13_value IS NULL OR (char_length(isbn_13_value) = 13
             AND char_length(translate(isbn_13_value, '0123456789', '')) = 0))),
    CONSTRAINT user_edition_metadata_override_publisher_value CHECK
        (publisher_is_overridden OR publisher_value IS NULL),
    CONSTRAINT user_edition_metadata_override_publication_date_value CHECK (
        (publication_date_is_overridden
         OR (publication_year_value IS NULL AND publication_month_value IS NULL
             AND publication_day_value IS NULL))
        AND ((publication_year_value IS NULL AND publication_month_value IS NULL
              AND publication_day_value IS NULL)
             OR (publication_year_value IS NOT NULL
                 AND publication_year_value BETWEEN 1 AND 9999
                 AND (publication_month_value IS NULL OR publication_month_value BETWEEN 1 AND 12)
                 AND (publication_month_value IS NOT NULL OR publication_day_value IS NULL)
                 AND (publication_day_value IS NULL OR publication_day_value BETWEEN 1 AND
                     CASE publication_month_value
                         WHEN 2 THEN CASE
                             WHEN publication_year_value % 400 = 0
                                 OR (publication_year_value % 4 = 0
                                     AND publication_year_value % 100 <> 0)
                             THEN 29 ELSE 28 END
                         WHEN 4 THEN 30 WHEN 6 THEN 30 WHEN 9 THEN 30 WHEN 11 THEN 30
                         ELSE 31
                     END)))),
    CONSTRAINT user_edition_metadata_override_page_count_value CHECK
        ((page_count_is_overridden OR page_count_value IS NULL)
         AND (page_count_value IS NULL OR page_count_value > 0)),
    CONSTRAINT user_edition_metadata_override_language_value CHECK
        (language_is_overridden OR language_value IS NULL),
    CONSTRAINT user_edition_metadata_override_description_value CHECK
        (description_is_overridden OR description_value IS NULL)
);

CREATE TABLE user_edition_author_override (
    user_edition_id uuid NOT NULL,
    authors_is_overridden boolean NOT NULL DEFAULT true,
    position integer NOT NULL,
    name text NOT NULL,
    CONSTRAINT user_edition_author_override_pkey PRIMARY KEY (user_edition_id, position),
    CONSTRAINT user_edition_author_override_metadata_fkey
        FOREIGN KEY (user_edition_id, authors_is_overridden)
        REFERENCES user_edition_metadata_override (user_edition_id, authors_is_overridden)
        ON DELETE CASCADE,
    CONSTRAINT user_edition_author_override_enabled CHECK (authors_is_overridden),
    CONSTRAINT user_edition_author_override_position_nonnegative CHECK (position >= 0),
    CONSTRAINT user_edition_author_override_name_nonblank CHECK (char_length(trim(name)) > 0)
);
