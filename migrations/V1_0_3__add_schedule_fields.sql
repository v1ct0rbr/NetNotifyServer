-- Flyway migration: add scheduled messaging fields

ALTER TABLE message ADD COLUMN IF NOT EXISTS schedule_days_of_week VARCHAR(20);
ALTER TABLE message ADD COLUMN IF NOT EXISTS schedule_time TIME;

COMMENT ON COLUMN message.schedule_days_of_week IS 'Comma-separated ISO day-of-week numbers (1=Mon..7=Sun), e.g. "1,2,3,4,5"';
COMMENT ON COLUMN message.schedule_time IS 'Fixed time of day for scheduled sends, e.g. 09:00:00';
