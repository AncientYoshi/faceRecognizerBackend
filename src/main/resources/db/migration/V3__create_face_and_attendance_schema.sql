CREATE TABLE face_registrations (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL UNIQUE REFERENCES students(id) ON DELETE CASCADE,
    embedding_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE attendance (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id),
    course_id UUID NOT NULL REFERENCES courses(id),
    session_id UUID NOT NULL REFERENCES attendance_sessions(id),
    attendance_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    similarity_score DECIMAL(6, 5) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_attendance_session_student UNIQUE (session_id, student_id),
    CONSTRAINT chk_attendance_similarity CHECK (similarity_score >= 0 AND similarity_score <= 1)
);

CREATE INDEX idx_attendance_student_id ON attendance(student_id);
CREATE INDEX idx_attendance_course_id ON attendance(course_id);
CREATE INDEX idx_attendance_session_id ON attendance(session_id);
CREATE INDEX idx_attendance_verified_at ON attendance(verified_at);
