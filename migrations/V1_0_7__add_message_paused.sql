ALTER TABLE message ADD COLUMN IF NOT EXISTS paused BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE message
SET paused = FALSE
WHERE paused IS NULL;

COMMENT ON COLUMN message.paused IS 'When true, the scheduler and agent feeds must ignore the message.';
