CREATE TABLE timetable_entries
(
    id             UUID PRIMARY KEY,
    course_id      UUID         NOT NULL,
    day_of_week    VARCHAR(9)   NOT NULL,
    day_order      INTEGER     NOT NULL,
    start_time     TIME         NOT NULL,
    end_time       TIME         NOT NULL,
    room           VARCHAR(100),
    effective_from DATE,
    effective_to   DATE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_timetable_course
        FOREIGN KEY (course_id)
            REFERENCES courses (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_timetable_day
        CHECK (
            day_of_week IN (
                            'MONDAY',
                            'TUESDAY',
                            'WEDNESDAY',
                            'THURSDAY',
                            'FRIDAY',
                            'SATURDAY',
                            'SUNDAY'
                )
            ),

    CONSTRAINT chk_timetable_day_order
        CHECK (day_order BETWEEN 1 AND 7),

    CONSTRAINT chk_timetable_time
        CHECK (start_time < end_time),

    CONSTRAINT chk_timetable_effective_dates
        CHECK (
            effective_to IS NULL
                OR effective_from IS NULL
                OR effective_from <= effective_to
            )
);

CREATE INDEX idx_timetable_course
    ON timetable_entries (course_id);

CREATE INDEX idx_timetable_day_time
    ON timetable_entries (day_order, start_time);

CREATE INDEX idx_timetable_course_day
    ON timetable_entries (course_id, day_of_week);

CREATE INDEX idx_timetable_active
    ON timetable_entries (active);
