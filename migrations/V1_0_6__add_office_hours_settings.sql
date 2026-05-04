CREATE TABLE IF NOT EXISTS office_hours_settings (
    id INTEGER PRIMARY KEY,
    availability_windows TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO office_hours_settings (id, availability_windows, updated_at)
SELECT
    1,
    '[{"day":"1","startTime":"08:00","endTime":"17:00"},{"day":"2","startTime":"08:00","endTime":"17:00"},{"day":"3","startTime":"08:00","endTime":"17:00"},{"day":"4","startTime":"08:00","endTime":"17:00"},{"day":"5","startTime":"08:00","endTime":"17:00"}]',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM office_hours_settings
    WHERE id = 1
);
