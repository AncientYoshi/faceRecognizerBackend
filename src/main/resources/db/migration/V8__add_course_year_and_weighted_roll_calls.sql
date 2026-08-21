ALTER TABLE courses
    ADD COLUMN study_year INTEGER;

ALTER TABLE courses
    ADD CONSTRAINT chk_courses_study_year
        CHECK (study_year IS NULL OR study_year BETWEEN 1 AND 6);

ALTER TABLE attendance_sessions
    ADD COLUMN roll_call_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE attendance_sessions
    ADD CONSTRAINT chk_attendance_sessions_roll_call_count
        CHECK (roll_call_count BETWEEN 1 AND 6);

CREATE INDEX idx_courses_department_study_year
    ON courses(department_id, study_year);

CREATE INDEX idx_attendance_sessions_course_date
    ON attendance_sessions(course_id, session_date);
