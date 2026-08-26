-- DEMO/TEST DATA ONLY.
--
-- Adds missing PRESENT rows until each course with eligible sessions reaches
-- at least the chosen target percentage for the selected cohort. Because the
-- application calculates a student's overall value as the arithmetic mean of
-- course percentages, bringing each course to the target also brings the
-- overall value to at least that target (except courses with zero eligible
-- sessions, which cannot receive attendance).
--
-- This file ends with ROLLBACK for safety. Review the preview, then change the
-- final ROLLBACK to COMMIT only when operating on disposable demo/test data.

BEGIN;

CREATE TEMPORARY TABLE demo_attendance_parameters (
    department_code VARCHAR(30) NOT NULL,
    study_year INTEGER NOT NULL,
    target_percentage NUMERIC(5, 2) NOT NULL,
    confirmation VARCHAR(40) NOT NULL
) ON COMMIT DROP;

-- Change 75.00 to the demo percentage you want, for example 80.00 or 85.00.
INSERT INTO demo_attendance_parameters
    (department_code, study_year, target_percentage, confirmation)
VALUES
    ('MC', 5, 75.00, 'DISPOSABLE DEMO DATA ONLY');

DO $$
DECLARE
    parameters demo_attendance_parameters%ROWTYPE;
BEGIN
    SELECT * INTO STRICT parameters FROM demo_attendance_parameters;

    IF parameters.confirmation <> 'DISPOSABLE DEMO DATA ONLY' THEN
        RAISE EXCEPTION 'Demo-data confirmation text is missing';
    END IF;
    IF parameters.study_year NOT BETWEEN 1 AND 6 THEN
        RAISE EXCEPTION 'Study year must be between 1 and 6';
    END IF;
    IF parameters.target_percentage <= 0 OR parameters.target_percentage > 100 THEN
        RAISE EXCEPTION 'Target percentage must be greater than 0 and at most 100';
    END IF;
END $$;

-- One row represents one eligible session for one enrolled student. The
-- roll_call_count is the number of attendance calls contributed by that row.
CREATE TEMPORARY TABLE eligible_demo_sessions ON COMMIT DROP AS
SELECT
    enrollment.student_id,
    enrollment.course_id,
    session.id AS session_id,
    session.session_date,
    session.start_time,
    session.end_time,
    session.roll_call_count
FROM demo_attendance_parameters parameters
JOIN departments department
  ON upper(department.code) = upper(parameters.department_code)
JOIN students student
  ON student.department_id = department.id
 AND student.study_year = parameters.study_year
JOIN enrollments enrollment
  ON enrollment.student_id = student.id
JOIN courses course
  ON course.id = enrollment.course_id
JOIN attendance_sessions session
  ON session.course_id = course.id
WHERE session.status <> 'CANCELLED'
  AND session.start_time <= CURRENT_TIMESTAMP
  AND (session.status = 'CLOSED' OR session.end_time <= CURRENT_TIMESTAMP);

CREATE TEMPORARY TABLE demo_attendance_to_insert ON COMMIT DROP AS
WITH course_totals AS (
    SELECT
        eligible.student_id,
        eligible.course_id,
        sum(eligible.roll_call_count)::bigint AS eligible_calls
    FROM eligible_demo_sessions eligible
    GROUP BY eligible.student_id, eligible.course_id
),
present_totals AS (
    SELECT
        eligible.student_id,
        eligible.course_id,
        sum(eligible.roll_call_count)::bigint AS present_calls
    FROM eligible_demo_sessions eligible
    JOIN attendance attendance_record
      ON attendance_record.session_id = eligible.session_id
     AND attendance_record.student_id = eligible.student_id
     AND attendance_record.status = 'PRESENT'
    GROUP BY eligible.student_id, eligible.course_id
),
missing_ranked AS (
    SELECT
        eligible.*,
        sum(eligible.roll_call_count) OVER (
            PARTITION BY eligible.student_id, eligible.course_id
            ORDER BY eligible.session_date, eligible.start_time, eligible.session_id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        )::bigint AS cumulative_missing_calls
    FROM eligible_demo_sessions eligible
    LEFT JOIN attendance attendance_record
      ON attendance_record.session_id = eligible.session_id
     AND attendance_record.student_id = eligible.student_id
    WHERE attendance_record.id IS NULL
),
requirements AS (
    SELECT
        total.student_id,
        total.course_id,
        total.eligible_calls,
        coalesce(present.present_calls, 0)::bigint AS present_calls,
        greatest(
            ceil(total.eligible_calls * parameters.target_percentage / 100.0)::bigint
                - coalesce(present.present_calls, 0)::bigint,
            0
        ) AS calls_needed
    FROM course_totals total
    CROSS JOIN demo_attendance_parameters parameters
    LEFT JOIN present_totals present
      ON present.student_id = total.student_id
     AND present.course_id = total.course_id
)
SELECT
    missing.student_id,
    missing.course_id,
    missing.session_id,
    missing.end_time,
    missing.roll_call_count,
    requirement.eligible_calls,
    requirement.present_calls,
    requirement.calls_needed
FROM missing_ranked missing
JOIN requirements requirement
  ON requirement.student_id = missing.student_id
 AND requirement.course_id = missing.course_id
WHERE requirement.calls_needed > 0
  AND missing.cumulative_missing_calls - missing.roll_call_count < requirement.calls_needed;

-- Preview every record that would be added.
SELECT
    student.student_number,
    course.code AS course_code,
    candidate.session_id,
    candidate.roll_call_count,
    candidate.eligible_calls,
    candidate.present_calls AS present_before,
    candidate.calls_needed
FROM demo_attendance_to_insert candidate
JOIN students student ON student.id = candidate.student_id
JOIN courses course ON course.id = candidate.course_id
ORDER BY student.student_number, course.code, candidate.session_id;

CREATE TEMPORARY TABLE inserted_demo_attendance ON COMMIT DROP AS
WITH inserted AS (
    INSERT INTO attendance (
        id,
        student_id,
        course_id,
        session_id,
        attendance_time,
        status,
        similarity_score,
        verified_at,
        created_at,
        updated_at,
        version
    )
    SELECT
        gen_random_uuid(),
        candidate.student_id,
        candidate.course_id,
        candidate.session_id,
        least(candidate.end_time, CURRENT_TIMESTAMP),
        'PRESENT',
        1.00000,
        least(candidate.end_time, CURRENT_TIMESTAMP),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    FROM demo_attendance_to_insert candidate
    ON CONFLICT (session_id, student_id) DO NOTHING
    RETURNING id, student_id, course_id, session_id
)
SELECT * FROM inserted;

-- Leave an explicit audit trail showing that these were demo seed records and
-- were not created by face verification.
INSERT INTO audit_logs (
    id,
    user_id,
    action,
    entity_type,
    entity_id,
    details,
    ip_address,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    NULL,
    'ATTENDANCE_RECORDED',
    'Attendance',
    inserted.id::text,
    'DEMO DATA: inserted by boost_demo_attendance_to_target.sql',
    'database-script',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM inserted_demo_attendance inserted;

SELECT count(*) AS attendance_rows_inserted
FROM inserted_demo_attendance;

-- SAFETY DEFAULT. Change only this line to COMMIT after reviewing the preview.
ROLLBACK;
