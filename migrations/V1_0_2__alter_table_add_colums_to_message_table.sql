ALTER TABLE message
    ADD COLUMN repeat_interval_minutes INT,
    ADD COLUMN expire_at TIMESTAMP,
    ADD COLUMN last_sent_at TIMESTAMP;

