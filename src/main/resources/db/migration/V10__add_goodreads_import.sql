CREATE TABLE goodreads_import (
    request_id uuid NOT NULL,
    user_id uuid NOT NULL,
    imported_count integer NOT NULL,
    already_present_count integer NOT NULL,
    reading_count integer NOT NULL,
    to_read_count integer NOT NULL,
    finished_count integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT goodreads_import_pkey PRIMARY KEY (user_id, request_id),
    CONSTRAINT goodreads_import_user_fkey FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT goodreads_import_counts_nonnegative CHECK (
        imported_count >= 0 AND already_present_count >= 0 AND reading_count >= 0
        AND to_read_count >= 0 AND finished_count >= 0)
);

CREATE INDEX goodreads_import_user_request_idx ON goodreads_import (user_id, request_id);
