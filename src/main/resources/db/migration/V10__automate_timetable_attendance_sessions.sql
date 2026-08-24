ALTER TABLE timetable_entries
    ADD COLUMN roll_call_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE timetable_entries
    ADD CONSTRAINT chk_timetable_roll_call_count
        CHECK (roll_call_count BETWEEN 1 AND 6);

ALTER TABLE attendance_sessions
    ADD COLUMN timetable_entry_id UUID;

ALTER TABLE attendance_sessions
    ADD CONSTRAINT fk_attendance_session_timetable
        FOREIGN KEY (timetable_entry_id)
            REFERENCES timetable_entries (id)
            ON DELETE SET NULL;

ALTER TABLE attendance_sessions
    ADD CONSTRAINT uq_attendance_session_timetable_date
        UNIQUE (timetable_entry_id, session_date);

CREATE INDEX idx_timetable_cohort_day
    ON timetable_entries(day_of_week, active, course_id);

CREATE INDEX idx_attendance_session_status_times
    ON attendance_sessions(status, start_time, end_time);

CREATE TABLE hardware_devices (
                                  id UUID PRIMARY KEY,
                                  device_id VARCHAR(100) NOT NULL UNIQUE,
                                  name VARCHAR(150) NOT NULL,
                                  key_hash VARCHAR(255) NOT NULL,
                                  room VARCHAR(100),
                                  course_id UUID REFERENCES courses(id),
                                  enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                  version BIGINT NOT NULL DEFAULT 0,
                                  CONSTRAINT chk_hardware_device_binding CHECK (room IS NOT NULL OR course_id IS NOT NULL)
);

CREATE INDEX idx_hardware_devices_course_id ON hardware_devices(course_id);
CREATE INDEX idx_hardware_devices_enabled ON hardware_devices(enabled);