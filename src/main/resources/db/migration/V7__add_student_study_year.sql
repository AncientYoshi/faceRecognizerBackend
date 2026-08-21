ALTER TABLE students
    ADD COLUMN study_year INTEGER;

ALTER TABLE students
    ADD CONSTRAINT chk_students_study_year
        CHECK (study_year IS NULL OR study_year BETWEEN 1 AND 6);

CREATE INDEX idx_students_department_study_year_number
    ON students(department_id, study_year, student_number);
