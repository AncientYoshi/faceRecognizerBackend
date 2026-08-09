# ESP32-CAM attendance integration

The ESP32 sends only a JPEG, a device ID/key, and an active attendance-session ID. Spring Boot determines the eligible students, asks FastAPI to identify the face, validates the result, and records attendance.

```text
ESP32-CAM
  -> POST Spring /hardware/v1/attendance/identify
  -> POST FastAPI /faces/identify
  -> Spring records attendance
  -> ESP32 displays roll number and sounds buzzer
```

## Spring Boot configuration

Set real values in the environment before starting Spring Boot:

```bash
export HARDWARE_DEVICE_ID=CLASSROOM-01
export HARDWARE_DEVICE_KEY='replace-with-a-long-random-device-secret'
```

The prototype supports one configured device. Use HTTPS and a device table with hashed, individually revocable secrets before deploying multiple classroom devices.

Test Spring before flashing the ESP32:

```bash
curl -X POST 'http://localhost:8080/hardware/v1/attendance/identify?sessionId=ACTIVE_SESSION_UUID' \
  -H 'X-Device-Id: CLASSROOM-01' \
  -H 'X-Device-Key: replace-with-a-long-random-device-secret' \
  -F 'image=@/absolute/path/face.jpg'
```

Successful new attendance:

```json
{
  "verified": true,
  "reason": "ATTENDANCE_VERIFIED",
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

Before flashing, edit these sketch constants:

```cpp
WIFI_SSID
WIFI_PASSWORD
SERVER_HOST
SERVER_PORT
SESSION_ID
DEVICE_ID
DEVICE_KEY
```

`SERVER_HOST` must be the LAN IP of the Spring Boot machine. On macOS, check it with `ipconfig getifaddr en0`. The computer and ESP32 must be on the same reachable network, and the firewall must allow TCP port 8080.

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
