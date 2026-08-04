ALTER TABLE user_edition
    ADD CONSTRAINT user_edition_state_dates CHECK (
        (state = 'TO_READ' AND started_on IS NULL AND finished_on IS NULL)
        OR (state = 'READING' AND started_on IS NOT NULL AND finished_on IS NULL)
        OR (state = 'FINISHED' AND finished_on IS NOT NULL)),
    ADD CONSTRAINT user_edition_date_chronology CHECK (
        started_on IS NULL OR finished_on IS NULL OR finished_on >= started_on);
