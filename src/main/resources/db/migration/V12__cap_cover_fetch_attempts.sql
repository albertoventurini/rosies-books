UPDATE cover_fetch_task
SET status = 'NO_COVER',
    lease_until = NULL,
    completed_at = CURRENT_TIMESTAMP
WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
  AND attempt_count >= 3;
