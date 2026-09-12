# ESP32-CAM attendance integration

The ESP32 sends only a JPEG and its device ID/key. Spring Boot resolves the active attendance session from the device's room/course binding, determines the eligible students, asks FastAPI to identify the face, validates the result, and records attendance. Course `studyYear`, session ID, and `rollCallCount` are server-side values and must not be sent by the ESP32. If a session has `rollCallCount: 3`, one successful device verification counts all three calls as present in reports.

```text
ESP32-CAM
  -> POST Spring /hardware/v1/attendance/identify
  -> POST FastAPI /faces/identify
  -> Spring records attendance
  -> ESP32 displays roll number and sounds buzzer
```

## Spring Boot configuration

The environment values remain as a compatibility fallback for the older explicit-`sessionId` request:

```bash
export HARDWARE_DEVICE_ID=CLASSROOM-01
export HARDWARE_DEVICE_KEY='replace-with-a-long-random-device-secret'
```

Automatic discovery uses database-backed devices. Log in as an administrator, generate a long random key, and register each camera. Bind it to a room, a course, or both:

```bash
openssl rand -base64 32

curl -X POST 'https://smart-attendance-api.duckdns.org/hardware-devices' \
  -H 'Authorization: Bearer ADMIN_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "deviceId": "CLASSROOM-01",
    "name": "Automation Lab Camera",
    "deviceKey": "PASTE_THE_GENERATED_SECRET_HERE",
    "room": "Automation Lab",
    "courseId": null,
    "enabled": true
  }'
```

The plaintext `deviceKey` is accepted only in the create/update request; Spring stores a BCrypt hash and never returns the secret. Keep the generated value because it must be flashed into that ESP32.

Administrator device-management endpoints:

- `GET /hardware-devices?query=&enabled=true&page=0&size=20`
- `GET /hardware-devices/{id}`
- `POST /hardware-devices`
- `PUT /hardware-devices/{id}` — set `newDeviceKey` to rotate the secret, or `null` to keep it
- `DELETE /hardware-devices/{id}`

`room` must correspond to the attendance session room (comparison ignores case and surrounding spaces). A session uses its explicit `room`, or the linked timetable room when no explicit room is assigned. A course-only binding is useful for a movable course camera. Setting both requires both to match. If no session matches, or more than one session matches, Spring returns `409` and records nothing.

For a camera shared by courses in one room, select **No course restriction** (`courseId: null`) and enter the physical room in the device settings. In **Create session**, enter the same room. Manual `POST /attendance-sessions` and scheduled-session `PUT /attendance-sessions/{id}` accept an optional `room` (up to 100 characters), for example:

```json
{
  "courseId": "YOUR_COURSE_UUID",
  "sessionDate": "2026-09-12",
  "startTime": "2026-09-12T06:00:00Z",
  "endTime": "2026-09-12T07:00:00Z",
  "rollCallCount": 1,
  "room": "Automation Lab"
}
```

The device discovers that session while it is `ACTIVE` and within its start/end times. An old manual session without a room cannot match a room-bound device: edit it while scheduled, or close it and create a new session with the correct room. A device ID such as `ROOM101` does not automatically populate its room. Two active sessions in the same room remain ambiguous; close one or add a course restriction. No ESP32 firmware change is required for this behavior.

Test the deployed Spring API before flashing the ESP32:

```bash
curl 'https://smart-attendance-api.duckdns.org/hardware/v1/attendance/active-session' \
  -H 'X-Device-Id: CLASSROOM-01' \
  -H 'X-Device-Key: PASTE_THE_GENERATED_SECRET_HERE'

curl -X POST 'https://smart-attendance-api.duckdns.org/hardware/v1/attendance/identify' \
  -H 'X-Device-Id: CLASSROOM-01' \
  -H 'X-Device-Key: PASTE_THE_GENERATED_SECRET_HERE' \
  -F 'image=@/absolute/path/face.jpg'
```

Successful new attendance:

```json
{
  "verified": true,
  "reason": "ATTENDANCE_VERIFIED",
  "sessionId": "62d2581e-cf21-4e92-af33-37ab45fa7c34",
  "courseCode": "VMC-501",
  "rollCallCount": 3,
  "studentId": "65788c5d-629c-426d-986d-eb77f42b7895",
  "rollNumber": "VMC 11",
  "attendanceRecorded": true,
  "attendanceId": "9b764b78-8084-4aed-a898-c91d473841be",
  "similarity": 0.93
}
```

A repeated scan returns `verified: true`, `attendanceRecorded: false`, and `reason: "ALREADY_VERIFIED"`.

## ESP32 setup

The sketch at [`esp32-cam/smart_attendance_esp32.ino`](esp32-cam/smart_attendance_esp32.ino) targets the AI-Thinker ESP32-CAM with OV2640 and no microSD card. Install:

- ESP32 board package by Espressif
- Adafruit SSD1306
- Adafruit GFX
- ArduinoJson 7

Example wiring:

```text
SSD1306 SDA -> GPIO13
SSD1306 SCL -> GPIO14
Buzzer      -> GPIO15 through 1 kOhm resistor and a 2N2222 transistor
Grounds     -> common GND
```

GPIO15 is a boot-strapping pin. Keep the buzzer driver from pulling it high during boot. Use a stable 5 V supply capable of powering the ESP32-CAM; camera/Wi-Fi current spikes commonly cause resets on weak USB adapters.

The supplied sketch is configured for `smart-attendance-api.duckdns.org` over certificate-verified HTTPS on port 443. Before flashing, edit these constants:

```cpp
WIFI_SSID
WIFI_PASSWORD
DEVICE_ID
DEVICE_KEY
```

`DEVICE_ID` and `DEVICE_KEY` must match the database-backed device registered through `/hardware-devices`. The timetable automation creates, starts, and closes sessions; the ESP32 discovers the one active session matching its binding. No session UUID needs to be flashed or changed. Do not put `studyYear` or `rollCallCount` in the device sketch.

For backward compatibility, the older environment-configured device can still call `POST /hardware/v1/attendance/identify?sessionId=...`. It cannot use automatic discovery because it has no database room/course binding.

The ESP32 synchronizes UTC time through NTP before connecting because TLS certificate validation requires a valid clock. The embedded trust anchor is Let's Encrypt ISRG Root X1, not the renewable DuckDNS leaf certificate, so normal Certbot renewal does not require reflashing the board.

## FastAPI requirement

Implement exactly one additional route:

```http
POST /faces/identify
Content-Type: multipart/form-data

image=<JPEG file>
candidateStudentIds=["uuid-1","uuid-2"]
```

`candidateStudentIds` is a JSON-array string in a form field. Spring sends only students enrolled in the session's course who have a registered face.

Required response:

```json
{
  "matched": true,
  "studentId": "65788c5d-629c-426d-986d-eb77f42b7895",
  "similarity": 0.93,
  "livenessPassed": true,
  "reason": "MATCHED"
}
```

No match response:

```json
{
  "matched": false,
  "studentId": null,
  "similarity": 0.41,
  "livenessPassed": true,
  "reason": "NOT_MATCHED"
}
```

Recommended reasons are `MATCHED`, `NOT_MATCHED`, `NO_FACE_DETECTED`, `MULTIPLE_FACES`, and `LIVENESS_FAILED`. `similarity` must be between 0 and 1. When `matched` is true, `studentId` must be one of the supplied candidates.

FastAPI route template:

```python
import json
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from pydantic import BaseModel

router = APIRouter(prefix="/faces", tags=["faces"])

# Import your existing service instance here, for example:
# from app.services.face_service import face_service


class IdentifyResponse(BaseModel):
    matched: bool
    studentId: UUID | None
    similarity: float
    livenessPassed: bool
    reason: str


@router.post("/identify", response_model=IdentifyResponse)
async def identify_face(
    image: Annotated[UploadFile, File(...)],
    candidateStudentIds: Annotated[str, Form(...)],
) -> IdentifyResponse:
    try:
        candidate_ids = [UUID(value) for value in json.loads(candidateStudentIds)]
    except (ValueError, TypeError, json.JSONDecodeError) as exc:
        raise HTTPException(400, "candidateStudentIds must be a JSON UUID array") from exc

    if not candidate_ids:
        raise HTTPException(400, "At least one candidate is required")
    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(400, "Image is empty")

    # Reuse the detector/embedding code from /faces/verify:
    # 1. detect exactly one face and check liveness;
    # 2. generate its embedding;
    # 3. load embeddings only for candidate_ids;
    # 4. select the highest cosine-similarity result;
    # 5. matched = best_similarity >= your configured threshold.
    result = face_service.identify(image_bytes, candidate_ids)

    return IdentifyResponse(
        matched=result.matched,
        studentId=result.student_id,
        similarity=result.similarity,
        livenessPassed=result.liveness_passed,
        reason=result.reason,
    )
```

`face_service.identify(...)` should read the same student-to-embedding store populated by `/faces/register`. FastAPI does not need direct access to the Spring/PostgreSQL academic tables.

If your AI service does not yet perform liveness detection, `livenessPassed: true` can keep the bench prototype working, but it is not anti-spoofing protection and must not be presented as such in production.
