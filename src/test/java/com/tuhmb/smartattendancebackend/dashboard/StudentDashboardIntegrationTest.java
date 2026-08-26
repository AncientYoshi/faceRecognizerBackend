package com.tuhmb.smartattendancebackend.dashboard;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.domain.AttendanceSession;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.repository.AuditLogRepository;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.face.repository.FaceRegistrationRepository;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentDashboardIntegrationTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Yangon");

    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired FaceRegistrationRepository faceRegistrationRepository;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        attendanceRepository.deleteAll();
        faceRegistrationRepository.deleteAll();
        sessionRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        departmentRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        teacherRepository.deleteAll();
        studentRepository.deleteAll();
        userRepository.deleteAll();

        today = LocalDate.now(ZONE_ID);
        transactionTemplate.executeWithoutResult(status -> createDashboardData());
    }

    @Test
    void studentDashboardUsesCompleteAttendanceHistoryAndConfiguredCurrentTerm() throws Exception {
        String studentToken = login("dashboard.student@example.com", "student-password");

        mockMvc.perform(get("/dashboard/student")
                        .param("date", today.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallAttendancePercentage").value(66.67))
                .andExpect(jsonPath("$.currentMonthPercentage").value(50.0))
                .andExpect(jsonPath("$.previousMonthPercentage").value(100.0))
                .andExpect(jsonPath("$.monthlyChange").value(-50.0))
                .andExpect(jsonPath("$.classesAttended").value(2))
                .andExpect(jsonPath("$.eligibleSessions").value(3))
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.absentCount").value(1))
                .andExpect(jsonPath("$.todaySessionCount").value(3))
                .andExpect(jsonPath("$.activeCourseCount").value(1))
                .andExpect(jsonPath("$.requiredAttendancePercentage").value(75.0))
                .andExpect(jsonPath("$.todaySessions.length()").value(3))
                .andExpect(jsonPath("$.todaySessions[0].courseCode").value("DASH-101"))
                .andExpect(jsonPath("$.lateCount").doesNotExist());
    }

    @Test
    void studentDashboardRejectsTeacherTokens() throws Exception {
        String teacherToken = login("dashboard.teacher@example.com", "teacher-password");

        mockMvc.perform(get("/dashboard/student")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());

        String studentToken = login("dashboard.student@example.com", "student-password");
        mockMvc.perform(get("/reports/attendance/students/overall")
                        .param("studyYear", "5")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/reports/attendance/students/overall/export/excel")
                        .param("studyYear", "5")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/students/me/attendance-summary")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentAttendanceSummaryReturnsEveryCourseForTheSelectedMonth() throws Exception {
        String studentToken = login("dashboard.student@example.com", "student-password");

        mockMvc.perform(get("/students/me/attendance-summary")
                        .param("period", "MONTH")
                        .param("date", today.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyYear").value(5))
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.courseCount").value(1))
                .andExpect(jsonPath("$.calculationMethod")
                        .value("ARITHMETIC_MEAN_OF_COURSE_PERCENTAGES"))
                .andExpect(jsonPath("$.averageAttendancePercentage").value(50.0))
                .andExpect(jsonPath("$.totalEligibleRollCalls").value(2))
                .andExpect(jsonPath("$.totalPresentRollCalls").value(1))
                .andExpect(jsonPath("$.totalAbsentRollCalls").value(1))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].courseCode").value("DASH-101"))
                .andExpect(jsonPath("$.courses[0].attendancePercentage").value(50.0));
    }

    @Test
    void overallPercentageIsTheAverageOfAllSevenAssignedCourses() throws Exception {
        transactionTemplate.executeWithoutResult(status -> addFullyAttendedCourses(7));

        String studentToken = login("dashboard.student@example.com", "student-password");
        mockMvc.perform(get("/dashboard/student")
                        .param("date", today.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallAttendancePercentage").value(95.24));

        mockMvc.perform(get("/students/me/attendance-summary")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCount").value(7))
                .andExpect(jsonPath("$.averageAttendancePercentage").value(95.24))
                .andExpect(jsonPath("$.totalEligibleRollCalls").value(9))
                .andExpect(jsonPath("$.totalPresentRollCalls").value(8))
                .andExpect(jsonPath("$.totalAbsentRollCalls").value(1))
                .andExpect(jsonPath("$.courses.length()").value(7))
                .andExpect(jsonPath("$.courses[0].courseCode").value("DASH-101"))
                .andExpect(jsonPath("$.courses[0].attendancePercentage").value(66.67))
                .andExpect(jsonPath("$.courses[6].courseCode").value("DASH-107"))
                .andExpect(jsonPath("$.courses[6].attendancePercentage").value(100.0));

        String teacherToken = login("dashboard.teacher@example.com", "teacher-password");
        mockMvc.perform(get("/reports/attendance/students/overall")
                        .param("studyYear", "5")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentCode").value("DASH"))
                .andExpect(jsonPath("$.studyYear").value(5))
                .andExpect(jsonPath("$.calculationMethod")
                        .value("ARITHMETIC_MEAN_OF_COURSE_PERCENTAGES"))
                .andExpect(jsonPath("$.students.totalElements").value(1))
                .andExpect(jsonPath("$.students.content[0].studentNumber").value("DASH-S-001"))
                .andExpect(jsonPath("$.students.content[0].studentName").value("Dashboard Student"))
                .andExpect(jsonPath("$.students.content[0].studyYear").value(5))
                .andExpect(jsonPath("$.students.content[0].courseCount").value(7))
                .andExpect(jsonPath("$.students.content[0].overallAttendancePercentage").value(95.24))
                .andExpect(jsonPath("$.students.content[0].totalEligibleRollCalls").value(9))
                .andExpect(jsonPath("$.students.content[0].totalPresentRollCalls").value(8))
                .andExpect(jsonPath("$.students.content[0].totalAbsentRollCalls").value(1));

        mockMvc.perform(get("/reports/attendance/students/overall")
                        .param("studyYear", "4")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(0));
    }

    @Test
    void teacherOverallAttendanceOrdersStudentNumbersNaturallyAcrossPages() throws Exception {
        transactionTemplate.executeWithoutResult(status -> createMixedFormatCohort());

        String teacherToken = login("dashboard.teacher@example.com", "teacher-password");
        mockMvc.perform(get("/reports/attendance/students/overall")
                        .param("studyYear", "5")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .param("size", "20")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(26))
                .andExpect(jsonPath("$.students.content[*].studentNumber").value(contains(
                        "V MC-1",
                        "VMC-2",
                        "VMC-3",
                        "VMc-4",
                        "VMc-5",
                        "VMc-6",
                        "VMC-7",
                        "VMC-8",
                        "V-MC-9",
                        "V-MC-10",
                        "VMC-11",
                        "V-MC-12",
                        "V-MC-13",
                        "VMC-14",
                        "VMC-15",
                        "VMC-16",
                        "VMC-17",
                        "VMC-18",
                        "VMC-19",
                        "VMC-20"
                )));

        mockMvc.perform(get("/reports/attendance/students/overall")
                        .param("studyYear", "5")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .param("page", "1")
                        .param("size", "5")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(26))
                .andExpect(jsonPath("$.students.totalPages").value(6))
                .andExpect(jsonPath("$.students.content[*].studentNumber").value(contains(
                        "VMc-6",
                        "VMC-7",
                        "VMC-8",
                        "V-MC-9",
                        "V-MC-10"
                )));
    }

    @Test
    void teacherCanExportOverallAttendanceInCohortMatrixFormat() throws Exception {
        transactionTemplate.executeWithoutResult(status -> createMixedFormatCohort());

        String teacherToken = login("dashboard.teacher@example.com", "teacher-password");
        MvcResult result = mockMvc.perform(get("/reports/attendance/students/overall/export/excel")
                        .param("studyYear", "5")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("student-overall-attendance-year-5-all-")
                ))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .andReturn();

        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray())
        )) {
            var sheet = workbook.getSheet("Overall");
            assertNotNull(sheet);
            assertEquals(
                    "DASH-101 Dashboard Calculations",
                    sheet.getRow(3).getCell(3).getStringCellValue()
            );
            List<String> exportedNumbers = java.util.stream.IntStream.range(4, 30)
                    .mapToObj(row -> sheet.getRow(row).getCell(1).getStringCellValue())
                    .toList();
            assertEquals(
                    List.of(
                            "V MC-1",
                            "VMC-2",
                            "VMC-3",
                            "VMc-4",
                            "VMc-5",
                            "VMc-6",
                            "VMC-7",
                            "VMC-8",
                            "V-MC-9",
                            "V-MC-10",
                            "VMC-11",
                            "V-MC-12",
                            "V-MC-13",
                            "VMC-14",
                            "VMC-15",
                            "VMC-16",
                            "VMC-17",
                            "VMC-18",
                            "VMC-19",
                            "VMC-20",
                            "VMC-21",
                            "VMC-22",
                            "VMC-23",
                            "VMC-24",
                            "VMC-25",
                            "VMC-123456789012345678901234567890"
                    ),
                    exportedNumbers
            );
            assertEquals(
                    66.67d,
                    sheet.getRow(4).getCell(3).getNumericCellValue(),
                    0.001d
            );
            assertEquals(
                    66.67d,
                    sheet.getRow(4).getCell(4).getNumericCellValue(),
                    0.001d
            );
            assertEquals(CellType.BLANK, sheet.getRow(5).getCell(3).getCellType());
            assertEquals(0d, sheet.getRow(5).getCell(4).getNumericCellValue());
        }

        MvcResult filteredResult = mockMvc.perform(get("/reports/attendance/students/overall/export/excel")
                        .param("studyYear", "5")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .param("query", "Student 25")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(filteredResult.getResponse().getContentAsByteArray())
        )) {
            var sheet = workbook.getSheet("Overall");
            assertEquals(
                    "DASH-101 Dashboard Calculations",
                    sheet.getRow(3).getCell(3).getStringCellValue()
            );
            assertEquals("VMC-25", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals(CellType.BLANK, sheet.getRow(4).getCell(3).getCellType());
            assertEquals(0d, sheet.getRow(4).getCell(4).getNumericCellValue());
        }

        assertTrue(auditLogRepository.findAll().stream().anyMatch(log ->
                log.getAction() == AuditAction.REPORT_DOWNLOADED
                        && "TeacherCohortAttendanceReport".equals(log.getEntityType())
        ));
    }

    @Test
    void attendanceAverageUsesSixAsDivisorWhenSixCoursesAreAssigned() throws Exception {
        transactionTemplate.executeWithoutResult(status -> addFullyAttendedCourses(6));

        String studentToken = login("dashboard.student@example.com", "student-password");
        mockMvc.perform(get("/students/me/attendance-summary")
                        .param("period", "ALL")
                        .param("date", today.toString())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCount").value(6))
                .andExpect(jsonPath("$.averageAttendancePercentage").value(94.45))
                .andExpect(jsonPath("$.totalEligibleRollCalls").value(8))
                .andExpect(jsonPath("$.totalPresentRollCalls").value(7))
                .andExpect(jsonPath("$.courses.length()").value(6));
    }

    private void createMixedFormatCohort() {
        List<String> studentNumbers = List.of(
                "V MC-1",
                "VMC-2",
                "VMC-3",
                "VMc-4",
                "VMc-5",
                "VMc-6",
                "VMC-7",
                "VMC-8",
                "V-MC-9",
                "V-MC-10",
                "VMC-11",
                "V-MC-12",
                "V-MC-13",
                "VMC-14",
                "VMC-15",
                "VMC-16",
                "VMC-17",
                "VMC-18",
                "VMC-19",
                "VMC-20",
                "VMC-21",
                "VMC-22",
                "VMC-23",
                "VMC-24",
                "VMC-25",
                "VMC-123456789012345678901234567890"
        );
        Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
        Department department = departmentRepository.findByCodeIgnoreCase("DASH").orElseThrow();
        Student firstStudent = studentRepository.findByStudentNumberIgnoreCase("DASH-S-001").orElseThrow();
        firstStudent.updateStudentNumber(studentNumbers.getFirst());

        for (int index = studentNumbers.size() - 1; index >= 1; index--) {
            int sequence = index + 1;
            AppUser user = userRepository.save(new AppUser(
                    "cohort.student.%d@example.com".formatted(sequence),
                    "unused-password-hash",
                    "Cohort",
                    "Student %d".formatted(sequence),
                    Set.of(studentRole)
            ));
            Student student = new Student(user, studentNumbers.get(index), 5);
            student.assignDepartment(department);
            studentRepository.save(student);
        }
    }

    private void addFullyAttendedCourses(int finalCourseNumber) {
        Student student = studentRepository.findByStudentNumberIgnoreCase("DASH-S-001").orElseThrow();
        Course existingCourse = courseRepository.findByCodeIgnoreCase("DASH-101").orElseThrow();
        Teacher teacher = existingCourse.getTeacher();
        Department department = existingCourse.getDepartment();
        Instant now = Instant.now();
        for (int number = 2; number <= finalCourseNumber; number++) {
            Course course = courseRepository.save(new Course(
                    "DASH-10" + number,
                    "Dashboard Course " + number,
                    "FIRST",
                    "2026-2027",
                    5,
                    department,
                    teacher
            ));
            enrollmentRepository.save(new Enrollment(student, course));
            LocalDate date = today.minusDays(number);
            AttendanceSession session = closedSession(
                    course,
                    teacher,
                    date,
                    now.minusSeconds(number * 10_000L),
                    now.minusSeconds(number * 10_000L - 3_600L)
            );
            attendanceRepository.save(new Attendance(
                    student,
                    course,
                    session,
                    new BigDecimal("0.96000"),
                    session.getEndTime().minusSeconds(60)
            ));
        }
    }

    private void createDashboardData() {
        Role teacherRole = roleRepository.findByName(RoleName.TEACHER).orElseThrow();
        Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
        AppUser teacherUser = userRepository.save(new AppUser(
                "dashboard.teacher@example.com",
                passwordEncoder.encode("teacher-password"),
                "Dashboard",
                "Teacher",
                Set.of(teacherRole)
        ));
        AppUser studentUser = userRepository.save(new AppUser(
                "dashboard.student@example.com",
                passwordEncoder.encode("student-password"),
                "Dashboard",
                "Student",
                Set.of(studentRole)
        ));
        Department department = departmentRepository.save(
                new Department("DASH", "Dashboard Department", null)
        );
        Teacher teacher = new Teacher(teacherUser, "DASH-T-001");
        teacher.assignDepartment(department);
        teacherRepository.save(teacher);
        Student student = new Student(studentUser, "DASH-S-001", 5);
        student.assignDepartment(department);
        studentRepository.save(student);
        Course course = courseRepository.save(new Course(
                "DASH-101",
                "Dashboard Calculations",
                "FIRST",
                "2026-2027",
                5,
                department,
                teacher
        ));
        enrollmentRepository.save(new Enrollment(student, course));

        Instant now = Instant.now();
        AttendanceSession activeSession = sessionRepository.save(new AttendanceSession(
                course,
                teacher,
                today,
                now.minusSeconds(60),
                now.plusSeconds(3600)
        ));
        activeSession.start();

        AttendanceSession currentPresent = closedSession(
                course,
                teacher,
                today,
                now.minusSeconds(10_800),
                now.minusSeconds(7_200)
        );
        attendanceRepository.save(new Attendance(
                student,
                course,
                currentPresent,
                new BigDecimal("0.95000"),
                now.minusSeconds(7_100)
        ));
        closedSession(
                course,
                teacher,
                today,
                now.minusSeconds(7_000),
                now.minusSeconds(3_600)
        );

        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        LocalDate previousDate = previousMonth.atDay(Math.min(15, previousMonth.lengthOfMonth()));
        Instant previousStart = previousDate.atTime(9, 0).atZone(ZONE_ID).toInstant();
        AttendanceSession previousPresent = closedSession(
                course,
                teacher,
                previousDate,
                previousStart,
                previousStart.plusSeconds(3_600)
        );
        attendanceRepository.save(new Attendance(
                student,
                course,
                previousPresent,
                new BigDecimal("0.93000"),
                previousStart.plusSeconds(1_800)
        ));

        AttendanceSession cancelled = sessionRepository.save(new AttendanceSession(
                course,
                teacher,
                today,
                now.plusSeconds(7_200),
                now.plusSeconds(10_800)
        ));
        cancelled.cancel();
    }

    private AttendanceSession closedSession(
            Course course,
            Teacher teacher,
            LocalDate date,
            Instant start,
            Instant end
    ) {
        AttendanceSession session = sessionRepository.save(
                new AttendanceSession(course, teacher, date, start, end)
        );
        session.start();
        session.close();
        return session;
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
