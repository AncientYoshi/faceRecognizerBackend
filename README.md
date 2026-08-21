# Smart Attendance Backend

Spring Boot is the core API for authentication, academic management, attendance, and coordination with the independent FastAPI face service.

## Milestone 1 features

- PostgreSQL persistence with Flyway migrations
- `User`, `Role`, `Student`, and `Teacher` foundation entities
- BCrypt password hashing
- Signed JWT access and refresh tokens
- Refresh-token rotation, replay protection, and logout revocation
- Role authorities (`ADMIN`, `TEACHER`, `STUDENT`)
- Public `POST /auth/register` for student or teacher self-registration
- Public `GET /public/departments` for the registration department selector
- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, and `GET /me`
- Admin-only user CRUD with search and pagination
- Automatic student/teacher profile creation when roles are assigned
- `GET /roles` for the system-defined role catalog
- Bean validation and consistent JSON errors
- Swagger UI and health endpoint
- CORS configuration for Angular

## Milestone 2 features

- Department CRUD with teacher and student assignment
- Course CRUD with department and assigned-teacher validation
- Course search by text, department, teacher, semester, and academic year
- Student enrollment with duplicate and department validation
- Course enrollment lists for admins and the assigned teacher
- A student can list their own enrolled courses
- Attendance-session scheduling and lifecycle management
- Session states: `SCHEDULED`, `ACTIVE`, `CLOSED`, and `CANCELLED`
- Only the assigned course teacher or an administrator can manage a course's sessions

## Milestone 3 features

- Blocking multipart client for the independent FastAPI face service
- Configurable AI-service connection and response timeouts
- Student face registration and re-registration
- Spring stores only the opaque FastAPI `embeddingId`, never the face embedding
- Student-only attendance verification using the authenticated JWT identity
- Enrollment, active-session, time-window, and prior-registration checks
- Unique attendance per student and session
- Failed matches return similarity without creating attendance
- Admin, assigned-teacher, and student-scoped attendance record queries
- JPEG, PNG, and WebP validation with a 10 MB upload limit

## Milestone 4 features

- Admin dashboard totals, today's attendance, attendance rate, recent sessions, and department statistics
- Teacher dashboard with assigned courses, today's sessions, and attendance summary
- Attendance reports filtered by course, student, department, date range, or month
- Paginated JSON reports plus downloadable PDF and Excel exports
- Teacher report scope is restricted to the teacher's assigned courses
- Append-only audit logs for login, recorded attendance, updates, deletes, report downloads, and setting changes
- Admin-managed system settings for attendance threshold, AI threshold, semester, and academic year
- Configurable reporting time zone through `APP_TIME_ZONE`

## Requirements

- Java 21 or newer
- Docker with Compose, or a PostgreSQL instance

## Run locally

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Set a secure JWT secret and enable the one-time administrator bootstrap:

```bash
export JWT_SECRET='replace-with-a-long-random-secret-containing-at-least-32-bytes'
export BOOTSTRAP_ADMIN_ENABLED=true
export BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export BOOTSTRAP_ADMIN_PASSWORD='change-this-password'
./mvnw spring-boot:run
```

The initializer only creates the administrator when the email does not already exist. Disable `BOOTSTRAP_ADMIN_ENABLED` after the first successful startup.

Default service URLs:

- Spring API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- FastAPI: `http://localhost:8000`

## Authentication examples

Load the department selector without authentication:

```bash
curl http://localhost:8080/public/departments
```

Register a student:

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "student@example.com",
    "password": "student-password",
    "firstName": "Alice",
    "lastName": "Student",
    "role": "STUDENT",
    "studentNumber": "STU-001",
    "studyYear": 5,
    "employeeNumber": null,
    "departmentId": "DEPARTMENT_UUID"
  }'
```

For teacher registration, use `"role":"TEACHER"`, provide `employeeNumber`, and leave `studentNumber` null. Public registration accepts exactly one role and never accepts `ADMIN`. A user cannot have both `STUDENT` and `TEACHER` roles through either self-registration or admin user management. Registration returns the new user/profile and department details; call `/auth/login` afterward to obtain tokens.

Login:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"change-this-password"}'
```

Use the returned access token:

```bash
curl http://localhost:8080/me \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

Refresh:

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

Each successful refresh revokes the submitted token and returns a new token pair.

Logout:

```bash
curl -i -X POST http://localhost:8080/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

## User management

An access token with the `ADMIN` role can use:

- `GET /users?query=alice&page=0&size=20`
- `GET /users/{id}`
- `POST /users`
- `PUT /users/{id}`
- `DELETE /users/{id}`
- `GET /roles`

Creating a student:

```bash
curl -X POST http://localhost:8080/users \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "student@example.com",
    "password": "student-password",
    "firstName": "Alice",
    "lastName": "Student",
    "roles": ["STUDENT"],
    "studentNumber": "STU-001",
    "studyYear": 5
  }'
```

`studentNumber` and `studyYear` (1 through 6) are required when assigning `STUDENT`, and `employeeNumber` is required when assigning `TEACHER`. `studyYear` must be omitted for non-students. `STUDENT` and `TEACHER` are mutually exclusive. Removing one of those roles removes its corresponding profile. The system-defined roles are seeded by Flyway and are intentionally not mutable.

The user response includes `studentId` and `teacherId`. These profile identifiers are used by the academic-management APIs.

## Academic management

Department APIs:

- `GET /public/departments` — public registration selector
- `GET /departments`
- `GET /departments/{id}`
- `POST /departments` — admin
- `PUT /departments/{id}` — admin
- `DELETE /departments/{id}` — admin
- `PUT /departments/{departmentId}/students/{studentId}` — admin
- `DELETE /departments/{departmentId}/students/{studentId}` — admin
- `PUT /departments/{departmentId}/teachers/{teacherId}` — admin
- `DELETE /departments/{departmentId}/teachers/{teacherId}` — admin
- `GET /departments/{departmentId}/students?studyYear=5&query=&page=0&size=20` — admin assignment list, optionally filtered by study year
- `GET /departments/{departmentId}/teachers?query=&page=0&size=20` — admin assignment list

The assignment-list responses include the profile ID, linked user ID, email, first/last/full name, student or employee number, student study year, and department ID/code/name. Search matches the reference number, email, first name, or last name.

Course and enrollment APIs:

- `GET /students/me` — current authenticated student's profile and `studentId`
- `GET /courses`
- `GET /courses/{id}`
- `POST /courses` — admin
- `PUT /courses/{id}` — admin
- `DELETE /courses/{id}` — admin
- `POST /courses/{courseId}/enrollments` — admin
- `DELETE /courses/{courseId}/enrollments/{studentId}` — admin
- `GET /courses/{courseId}/enrollments` — admin or assigned teacher
- `GET /students/{studentId}/courses` — admin or that student

Student clients should call `GET /students/me` after login and use its `studentId` for student-scoped APIs such as `GET /students/{studentId}/courses`. This endpoint uses the authenticated JWT subject and does not require access to the admin-only `/users/{id}` API.

### Student study-year grouping

Students are grouped by department, `studyYear` (1 through 6), and their official `studentNumber`. For example, a Fifth Year Mechatronics student can be stored as:

```json
{
  "studentNumber": "VMC-11",
  "studyYear": 5,
  "departmentId": "MECHATRONICS_DEPARTMENT_UUID"
}
```

Retrieve the Fifth Year Mechatronics roll-call list with:

```bash
curl "http://localhost:8080/departments/MECHATRONICS_DEPARTMENT_UUID/students?studyYear=5&page=0&size=100" \
  -H "Authorization: Bearer ADMIN_ACCESS_TOKEN"
```

The Flyway migration keeps `studyYear` nullable for students created before this feature. Assign those existing students a year through `PUT /users/{userId}`; all newly created or self-registered students must provide `studyYear`.

Course search accepts `query`, `departmentId`, `teacherId`, `semester`, `academicYear`, `page`, and `size`.

Academic consistency rules prevent moving an enrolled student to another department, moving a teacher who owns courses, duplicate enrollment, and assigning a course teacher from another department.

## Timetable

Administrators and teachers manage recurring timetable entries with:

- `GET /timetables`
- `GET /timetables/{id}`
- `POST /timetables`
- `PUT /timetables/{id}`
- `DELETE /timetables/{id}`

The student portal retrieves a weekly timetable with:

```http
GET /students/me/timetable?weekStart=2026-08-03
Authorization: Bearer STUDENT_ACCESS_TOKEN
```

The student is derived from the JWT. The response contains only active timetable entries for courses in which that student is enrolled. `weekStart` is optional and is normalized to Monday; when omitted, the current week in `app.time-zone` is returned. Each entry includes its actual date, course, semester, academic year, teacher, start/end time, room, and a `today` flag.

## Attendance sessions

- `GET /attendance-sessions`
- `GET /attendance-sessions/{id}`
- `POST /attendance-sessions`
- `PUT /attendance-sessions/{id}`
- `DELETE /attendance-sessions/{id}`
- `POST /attendance-sessions/{id}/start`
- `POST /attendance-sessions/{id}/close`
- `POST /attendance-sessions/{id}/cancel`

Only the assigned course teacher or an administrator can create or mutate sessions. Valid transitions are:

```text
SCHEDULED -> ACTIVE -> CLOSED
SCHEDULED -> CANCELLED
```

Only a `SCHEDULED` session can be edited or deleted.

### Student email reminder when attendance starts

When mail notifications are enabled, `POST /attendance-sessions/{id}/start` sends an individual reminder to every enabled student enrolled in the course. Messages are dispatched after the session transaction commits and contain a direct link to `/student/scan/{sessionId}`. A mail delivery failure is logged and does not roll back or close the active session.

Configure any SMTP provider with environment variables:

```bash
MAIL_NOTIFICATIONS_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=no-reply@example.com
MAIL_PASSWORD=replace-with-an-smtp-password
MAIL_FROM=no-reply@example.com
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLED=true
MAIL_STARTTLS_REQUIRED=true
FRONTEND_BASE_URL=https://smart-attendance.paiswanpyae2002.workers.dev
```

For Gmail SMTP, use `smtp.gmail.com`, port `587`, and a Google App Password rather than the account's normal password. Keep SMTP credentials only in the server environment file. Notifications default to disabled when `MAIL_NOTIFICATIONS_ENABLED` is missing or false.

## Face registration and attendance verification

Face registration is available to the student who owns the profile or an administrator:

```bash
curl -X POST http://localhost:8080/students/STUDENT_ID/face/register \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -F "image=@/absolute/path/face.jpg"
```

Spring forwards the image to:

```text
POST http://localhost:8000/faces/register
```

Registration metadata can be inspected with:

```text
GET /students/{studentId}/face
```

An authenticated student verifies attendance for an active session:

```bash
curl -X POST "http://localhost:8080/attendance/verify?sessionId=SESSION_ID" \
  -H "Authorization: Bearer STUDENT_ACCESS_TOKEN" \
  -F "image=@/absolute/path/face.jpg"
```

Spring derives the student identity from the access token, verifies enrollment and session eligibility, and then calls:

```text
POST http://localhost:8000/faces/verify
```

Attendance records:

- `GET /attendance`
- `GET /attendance/{id}`

Administrators can access all records. Teachers only see records for their assigned courses, and students only see their own records. `GET /attendance` accepts `sessionId`, `courseId`, `studentId`, `page`, and `size`.

When FastAPI reports `matched: false`, Spring returns the similarity score but does not create an attendance record. Verification requires an `ACTIVE` session and the current time must be inside its configured start/end window.

## ESP32-CAM hardware attendance

An ESP32-CAM can identify an enrolled student without a student JWT or student ID:

```text
POST /hardware/v1/attendance/identify?sessionId={activeSessionId}
X-Device-Id: configured device ID
X-Device-Key: configured device secret
multipart image: captured JPEG
```

Spring authenticates the device, limits AI candidates to face-registered students enrolled in the session's course, calls `POST /faces/identify` on FastAPI, and records attendance. The complete ESP32 sketch, wiring notes, test command, response contract, and FastAPI route template are in [`hardware/README.md`](hardware/README.md).

## Dashboards and reports

Dashboard APIs:

- `GET /dashboard/admin` — admin
- `GET /dashboard/teacher` — teacher
- `GET /dashboard/student?date=YYYY-MM-DD` — authenticated student; `date` defaults to today

Report APIs:

- `GET /reports/attendance` — admin or teacher
- `GET /reports/attendance/students` — weekly/monthly percentage list for admin or teacher, optionally filtered by `studyYear`
- `GET /reports/attendance/students/export/pdf` — weekly/monthly student percentage PDF
- `GET /reports/attendance/students/export/excel` — weekly/monthly student percentage Excel workbook
- `GET /reports/attendance/export/pdf` — admin or teacher
- `GET /reports/attendance/export/excel` — admin or teacher

All report endpoints accept optional `courseId`, `studentId`, `departmentId`, `from`, and `to` filters. Dates use `YYYY-MM-DD`. Use `month=YYYY-MM` instead of `from` and `to` for a monthly report. The JSON endpoint additionally accepts `page` and `size`.

Examples:

```bash
curl "http://localhost:8080/reports/attendance?month=2026-07" \
  -H "Authorization: Bearer ACCESS_TOKEN"

curl -OJ "http://localhost:8080/reports/attendance/export/pdf?courseId=COURSE_ID" \
  -H "Authorization: Bearer ACCESS_TOKEN"

curl -OJ "http://localhost:8080/reports/attendance/export/excel?departmentId=DEPARTMENT_ID" \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

Courses and attendance sessions now carry two fields used by the university roll-call model:

```json
{
  "studyYear": 5,
  "rollCallCount": 3
}
```

`studyYear` belongs to the course request and must match the enrolled student's year. `rollCallCount` belongs to the attendance-session request and is between 1 and 6. The total scheduled roll calls for one department and study-year cohort cannot exceed 6 on the same date. For example, a Monday 09:00–12:00 Industrial Automation session uses `rollCallCount: 3`; one successful face verification records all three consecutive calls as present.

Attendance totals are weighted by `rollCallCount`. Four weekly three-call sessions produce 12 monthly calls, and attendance at three of them produces 9 present calls and `9 / 12 = 75%`. The student dashboard's overall percentage is the arithmetic mean of the percentage for every enrolled course, including a zero percentage for a course that has no eligible calls yet. Thus, a student assigned to 7 courses has the sum of the 7 course percentages divided by 7.

The student PDF and Excel exports use the university register layout: one row per student, one narrow column per roll call, followed by absent, present, and percentage totals. PDF output is landscape and Excel creates one print-ready worksheet per course.

The attendance rate is `recorded weighted roll calls / expected weighted roll calls`. Expected attendance is calculated from eligible sessions, their `rollCallCount`, and course enrollments, so the dashboard and report denominator remains meaningful when attendance is missing. Existing legacy courses have a nullable study year; update each one through `PUT /courses/{courseId}` before enrolling students or scheduling new sessions.

The student dashboard is calculated from the complete attendance history and does not depend on a paginated `/attendance` response:

```bash
curl "http://localhost:8080/dashboard/student?date=2026-08-16" \
  -H "Authorization: Bearer STUDENT_ACCESS_TOKEN"
```

It returns overall, current-month, and previous-month attendance percentages; monthly change; present, absent, and eligible roll-call totals; the requested day's sessions; the configured attendance threshold; and the student's current-term course count. An eligible roll call belongs to a non-cancelled enrolled-course session that is closed or whose end time has passed. Because attendance currently supports only `PRESENT`, absence is derived as `eligibleSessions - presentCount`; there is no late value. The existing JSON field names retain `Sessions` for API compatibility, but their numeric values are weighted roll-call units. Current-term courses use `CURRENT_SEMESTER` and `CURRENT_ACADEMIC_YEAR`, and the required percentage uses `ATTENDANCE_THRESHOLD`.

Teachers can list every enrolled student and see present, absent, total-session, and percentage values for one of their assigned courses:

```bash
curl "http://localhost:8080/reports/attendance/students?period=WEEK&date=2026-08-10&courseId=COURSE_ID&studyYear=5&page=0&size=20" \
  -H "Authorization: Bearer TEACHER_ACCESS_TOKEN"

curl "http://localhost:8080/reports/attendance/students?period=MONTH&date=2026-08-10&courseId=COURSE_ID&query=STU-001" \
  -H "Authorization: Bearer TEACHER_ACCESS_TOKEN"

curl -OJ "http://localhost:8080/reports/attendance/students/export/pdf?period=WEEK&date=2026-08-10&courseId=COURSE_ID" \
  -H "Authorization: Bearer TEACHER_ACCESS_TOKEN"

curl -OJ "http://localhost:8080/reports/attendance/students/export/excel?period=MONTH&date=2026-08-10&courseId=COURSE_ID" \
  -H "Authorization: Bearer TEACHER_ACCESS_TOKEN"
```

`period` is `WEEK` or `MONTH`; it defaults to `WEEK`. `date` selects the week or month and defaults to today in the configured system time zone. Weekly reports run Monday through Sunday. `courseId` and `query` are optional. Search matches student number, email, first name, or last name. A teacher is always restricted to their assigned courses. The list starts from enrollments, so students with no attendance record are still returned with `0.00` percent.

## Audit logs and system settings

Admin-only administration APIs:

- `GET /audit-logs`
- `GET /system-settings`
- `GET /system-settings/{key}`
- `PUT /system-settings/{key}`

Audit-log search accepts `action`, `userId`, `from`, `to`, `page`, and `size`. Audit timestamps use ISO-8601 instants.

Update a setting:

```bash
curl -X PUT http://localhost:8080/system-settings/AI_SIMILARITY_THRESHOLD \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"value":"0.85"}'
```

Seeded setting keys are `ATTENDANCE_THRESHOLD`, `AI_SIMILARITY_THRESHOLD`, `CURRENT_SEMESTER`, and `CURRENT_ACADEMIC_YEAR`.

## Configuration

See `.env.example` for supported environment variables. Never use the development database password or default JWT secret in production.

## Tests

Tests use an isolated H2 database in PostgreSQL compatibility mode:

```bash
./mvnw test
```

## Postman

Import the ready-made collection and local environment from the [`postman`](postman) directory. The collection scripts automatically store JWTs and IDs as the setup requests run. See [`postman/README.md`](postman/README.md) for the import order and face-image setup.
