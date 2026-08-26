-- Normalize Mechatronics Year-5 student data for PostgreSQL/Neon.
--
-- Roll-number examples:
--   V MC-1  -> VMC-01
--   V-MC-9  -> VMC-09
--   V-MC-10 -> VMC-10
--   VMC-11  -> VMC-11
--
-- Run the preview SELECT statements first. For a dry run, change the final
-- COMMIT to ROLLBACK.

BEGIN;

CREATE TEMPORARY TABLE proposed_student_numbers ON COMMIT DROP AS
SELECT
    student.id AS student_id,
    student.student_number AS old_student_number,
    'VMC-' || to_char(
        substring(student.student_number FROM '([0-9]+)[[:space:]]*$')::integer,
        'FM00'
    ) AS new_student_number
FROM students student
JOIN departments department ON department.id = student.department_id
WHERE upper(department.code) = 'MC'
  AND student.study_year = 5
  AND student.student_number ~* '^[[:space:]]*V[[:space:]-]*MC[[:space:]-]*[0-9]+[[:space:]]*$';

-- Preview exactly what will change.
SELECT old_student_number, new_student_number
FROM proposed_student_numbers
WHERE old_student_number <> new_student_number
ORDER BY new_student_number;

-- Stop instead of partially updating when normalization would create a
-- duplicate student number.
DO $$
BEGIN
    IF EXISTS (
        SELECT proposed.new_student_number
        FROM proposed_student_numbers proposed
        GROUP BY proposed.new_student_number
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Normalization would create duplicate Year-5 roll numbers';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM proposed_student_numbers proposed
        JOIN students existing
          ON lower(existing.student_number) = lower(proposed.new_student_number)
         AND existing.id <> proposed.student_id
    ) THEN
        RAISE EXCEPTION 'A normalized roll number is already used by another student';
    END IF;
END $$;

UPDATE students student
SET student_number = proposed.new_student_number,
    updated_at = CURRENT_TIMESTAMP,
    version = student.version + 1
FROM proposed_student_numbers proposed
WHERE student.id = proposed.student_id
  AND student.student_number <> proposed.new_student_number;

-- Optional profile corrections. Add only verified values before running.
-- Email is also the login username, so changing it changes how the student
-- logs in. Keep this table empty if names and emails are already correct.
CREATE TEMPORARY TABLE student_profile_changes (
    student_number VARCHAR(80) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE
) ON COMMIT DROP;

-- Example only: remove the leading "--" and replace with real information.
-- INSERT INTO student_profile_changes
--     (student_number, first_name, last_name, email)
-- VALUES
--     ('VMC-01', 'Honey', 'Soe', 'honey.soe@your-university.edu'),
--     ('VMC-02', 'Hsue Ei', 'Hlaing', 'hsueei.hlaing@your-university.edu');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM student_profile_changes change
        JOIN students target_student
          ON lower(target_student.student_number) = lower(change.student_number)
        JOIN app_users existing ON lower(existing.email) = lower(change.email)
        WHERE existing.id <> target_student.user_id
    ) THEN
        RAISE EXCEPTION 'A replacement email is already used by another user';
    END IF;
END $$;

UPDATE app_users app_user
SET first_name = change.first_name,
    last_name = change.last_name,
    email = lower(change.email),
    updated_at = CURRENT_TIMESTAMP,
    version = app_user.version + 1
FROM students student
JOIN student_profile_changes change
  ON lower(change.student_number) = lower(student.student_number)
WHERE app_user.id = student.user_id;

-- Final verification for the teacher screen.
SELECT
    student.student_number,
    app_user.first_name,
    app_user.last_name,
    app_user.email,
    student.study_year,
    department.code AS department_code
FROM students student
JOIN app_users app_user ON app_user.id = student.user_id
JOIN departments department ON department.id = student.department_id
WHERE upper(department.code) = 'MC'
  AND student.study_year = 5
ORDER BY
    substring(student.student_number FROM '([0-9]+)$')::integer,
    student.student_number;

COMMIT;
