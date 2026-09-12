CREATE TABLE course_schedule_cancellations (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    from_date DATE NOT NULL,
    to_date DATE,
    reason VARCHAR(500) NOT NULL,
    cancelled_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT cancellation_dates_valid CHECK (to_date IS NULL OR to_date >= from_date)
);
CREATE INDEX idx_course_cancellations_dates ON course_schedule_cancellations(course_id, from_date);
