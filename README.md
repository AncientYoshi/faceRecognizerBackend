# Smart Attendance Backend

Spring Boot is the core API for authentication, academic management, attendance, and coordination with the independent FastAPI face service.

## Milestone 1 features

- PostgreSQL persistence with Flyway migrations
- `User`, `Role`, `Student`, and `Teacher` foundation entities
- BCrypt password hashing
- Signed JWT access and refresh tokens
- Refresh-token rotation, replay protection, and logout revocation
- Role authorities (`ADMIN`, `TEACHER`, `STUDENT`)
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
    "studentNumber": "STU-001"
  }'
```

`studentNumber` is required when assigning `STUDENT`, and `employeeNumber` is required when assigning `TEACHER`. Removing one of those roles removes its corresponding profile. The system-defined roles are seeded by Flyway and are intentionally not mutable.

The user response includes `studentId` and `teacherId`. These profile identifiers are used by the academic-management APIs.

## Academic management

Department APIs:

- `GET /departments`
- `GET /departments/{id}`
- `POST /departments` — admin
- `PUT /departments/{id}` — admin
- `DELETE /departments/{id}` — admin
- `PUT /departments/{departmentId}/students/{studentId}` — admin
- `DELETE /departments/{departmentId}/students/{studentId}` — admin
- `PUT /departments/{departmentId}/teachers/{teacherId}` — admin
- `DELETE /departments/{departmentId}/teachers/{teacherId}` — admin

Course and enrollment APIs:

- `GET /courses`
- `GET /courses/{id}`
- `POST /courses` — admin
- `PUT /courses/{id}` — admin
- `DELETE /courses/{id}` — admin
- `POST /courses/{courseId}/enrollments` — admin
- `DELETE /courses/{courseId}/enrollments/{studentId}` — admin
- `GET /courses/{courseId}/enrollments` — admin or assigned teacher
- `GET /students/{studentId}/courses` — admin or that student

Course search accepts `query`, `departmentId`, `teacherId`, `semester`, `academicYear`, `page`, and `size`.

Academic consistency rules prevent moving an enrolled student to another department, moving a teacher who owns courses, duplicate enrollment, and assigning a course teacher from another department.

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

## Dashboards and reports

Dashboard APIs:

- `GET /dashboard/admin` — admin
- `GET /dashboard/teacher` — teacher

Report APIs:

- `GET /reports/attendance` — admin or teacher
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

The attendance rate is `recorded attendance / expected attendance`. Expected attendance is calculated from eligible sessions and course enrollments, so the dashboard and report denominator remains meaningful when attendance is missing.

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
