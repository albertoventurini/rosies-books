CREATE TABLE cover_fetch_task (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    user_edition_id uuid NOT NULL,
    goodreads_request_id uuid,
    status varchar(32) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone NOT NULL,
    lease_until timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT cover_fetch_task_pkey PRIMARY KEY (id),
    CONSTRAINT cover_fetch_task_user_edition_key UNIQUE (user_edition_id),
    CONSTRAINT cover_fetch_task_user_fkey FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT cover_fetch_task_user_edition_fkey FOREIGN KEY (user_edition_id)
        REFERENCES user_edition (id) ON DELETE CASCADE,
    CONSTRAINT cover_fetch_task_import_fkey FOREIGN KEY (user_id, goodreads_request_id)
        REFERENCES goodreads_import (user_id, request_id) ON DELETE CASCADE,
    CONSTRAINT cover_fetch_task_status_check CHECK
        (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SUCCEEDED', 'NO_COVER')),
    CONSTRAINT cover_fetch_task_attempt_count_nonnegative CHECK (attempt_count >= 0)
);

CREATE INDEX cover_fetch_task_due_idx
    ON cover_fetch_task (status, next_attempt_at) WHERE status IN ('PENDING', 'RETRY');
CREATE INDEX cover_fetch_task_import_idx ON cover_fetch_task (user_id, goodreads_request_id);
