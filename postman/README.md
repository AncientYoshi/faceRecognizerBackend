# Smart Attendance Postman Files

Import both files into Postman:

1. `Smart-Attendance-Backend.postman_collection.json`
2. `Smart-Attendance-Local.postman_environment.json`

Select **Smart Attendance - Local** as the active environment, then update:

- `adminEmail` and `adminPassword` to match the bootstrapped administrator.
- `faceImagePath` to an absolute JPEG, PNG, or WebP file path. If Postman does not resolve the variable for a file field, open the request and select the image manually.
- `hardwareDeviceId` and `hardwareDeviceKey` to the values configured in Spring Boot before testing the ESP32 hardware request.

Run folders `00` through `11` in order. Request scripts automatically save access/refresh tokens and the IDs for the created teacher, student, department, course, session, and attendance record.

The FastAPI service must be running at `http://localhost:8000` before running folder `06` or `07`. Spring Boot must be running at `http://localhost:8080`.

Folder `99` contains destructive or mutually exclusive lifecycle operations. Run those requests individually, not with the main setup flow. Re-running setup against the same database can cause uniqueness conflicts; change the example emails/codes or clean up the prior test data first.
