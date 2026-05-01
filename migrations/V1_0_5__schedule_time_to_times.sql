-- Migra schedule_time (TIME) para schedule_times (VARCHAR) suportando múltiplos horários.
-- Tolerante a reexecução/estado parcial para facilitar recovery após falha anterior.
ALTER TABLE message ADD COLUMN IF NOT EXISTS schedule_times VARCHAR(200);

UPDATE message
SET schedule_times = COALESCE(schedule_times, SUBSTRING(CAST(schedule_time AS VARCHAR), 1, 5))
WHERE schedule_time IS NOT NULL;

ALTER TABLE message DROP COLUMN IF EXISTS schedule_time;
