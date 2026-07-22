ALTER TABLE notifications ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN next_retry_at TIMESTAMPTZ;

CREATE TABLE notification_attempts (
    id              BIGSERIAL PRIMARY KEY,
    notification_id BIGINT      NOT NULL REFERENCES notifications (id),
    attempt_number  INTEGER     NOT NULL,
    status          VARCHAR(50) NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notification_attempts_notification_attempt UNIQUE (notification_id, attempt_number)
);

CREATE INDEX idx_notification_attempts_notification_id ON notification_attempts (notification_id);
CREATE INDEX idx_notifications_next_retry_at ON notifications (next_retry_at);
