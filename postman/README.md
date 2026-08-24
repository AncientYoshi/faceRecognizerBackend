# Smart Attendance Postman Files

Import both files into Postman:

1. `Smart-Attendance-Backend.postman_collection.json`
2. `Smart-Attendance-Local.postman_environment.json`

Select **Smart Attendance - Local** as the active environment, then update:

- `adminEmail` and `adminPassword` to match the bootstrapped administrator.
- `faceImagePath` to an absolute JPEG, PNG, or WebP file path. If Postman does not resolve the variable for a file field, open the request and select the image manually.
- `hardwareDeviceId` and `hardwareDeviceKey` to a unique ID and a secret of at least 16 characters. Folder `07` registers that database-backed device against the created course before testing automatic session discovery.

Run folders `00` through `11` in order. Request scripts automatically save access/refresh tokens and the IDs for the created teacher, student, department, course, hardware device, session, and attendance record.

The FastAPI service must be running at `http://localhost:8000` before running folder `06` or `07`. Spring Boot must be running at `http://localhost:8080`.

Folder `99` contains destructive or mutually exclusive lifecycle operations. Run those requests individually, not with the main setup flow. Re-running setup against the same database can cause uniqueness conflicts; change the example emails/codes or clean up the prior test data first.

Folder `09 - Reports` includes **Teacher Year 5 - Overall Attendance by Student** for the teacher portal's year screen. Change `studyYear=5` to another year as needed; the request automatically uses the teacher token and only returns that teacher's department.
