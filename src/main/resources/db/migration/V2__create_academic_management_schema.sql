CREATE TABLE departments (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE students
    ADD COLUMN department_id UUID REFERENCES departments(id) ON DELETE SET NULL;

ALTER TABLE teachers
    ADD COLUMN department_id UUID REFERENCES departments(id) ON DELETE SET NULL;

CREATE INDEX idx_students_department_id ON students(department_id);
CREATE INDEX idx_teachers_department_id ON teachers(department_id);

CREATE TABLE courses (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    semester VARCHAR(40) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    department_id UUID NOT NULL REFERENCES departments(id),
    teacher_id UUID NOT NULL REFERENCES teachers(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_courses_department_id ON courses(department_id);
CREATE INDEX idx_courses_teacher_id ON courses(teacher_id);
CREATE INDEX idx_courses_term ON courses(academic_year, semester);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_enrollment_student_course UNIQUE (student_id, course_id)
);

CREATE INDEX idx_enrollments_student_id ON enrollments(student_id);
CREATE INDEX idx_enrollments_course_id ON enrollments(course_id);

CREATE TABLE attendance_sessions (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id),
    teacher_id UUID NOT NULL REFERENCES teachers(id),
    session_date DATE NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_attendance_session_times CHECK (end_time > start_time)
);

CREATE INDEX idx_attendance_sessions_course_id ON attendance_sessions(course_id);
CREATE INDEX idx_attendance_sessions_teacher_id ON attendance_sessions(teacher_id);
CREATE INDEX idx_attendance_sessions_date_status ON attendance_sessions(session_date, status);
