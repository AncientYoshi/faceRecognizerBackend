ALTER TABLE attendance_sessions
    ADD COLUMN idempotency_key UUID;

ALTER TABLE attendance_sessions
    ADD CONSTRAINT uq_attendance_sessions_idempotency_key UNIQUE (idempotency_key);
