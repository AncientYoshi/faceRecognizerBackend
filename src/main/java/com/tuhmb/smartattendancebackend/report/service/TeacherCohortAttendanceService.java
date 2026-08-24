package com.tuhmb.smartattendancebackend.report.service;

import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendancePeriod;
import com.tuhmb.smartattendancebackend.attendance.api.StudentAttendanceSummaryResponse;
import com.tuhmb.smartattendancebackend.attendance.service.StudentAttendanceSummaryService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.report.api.TeacherCohortAttendanceResponse;
import com.tuhmb.smartattendancebackend.report.api.TeacherStudentOverallAttendanceResponse;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TeacherCohortAttendanceService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final StudentAttendanceSummaryService summaryService;
    private final ZoneId zoneId;

    public TeacherCohortAttendanceService(
            TeacherRepository teacherRepository,
            StudentRepository studentRepository,
            StudentAttendanceSummaryService summaryService,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.summaryService = summaryService;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public TeacherCohortAttendanceResponse report(
            Integer studyYear,
            StudentAttendancePeriod period,
            LocalDate referenceDate,
            String query,
            int page,
            int size,
            Jwt jwt
    ) {
        Teacher teacher = teacherRepository.findByUserId(parseSubject(jwt))
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile was not found"));
        if (teacher.getDepartment() == null) {
            throw new ConflictException("Teacher must be assigned to a department before viewing student attendance");
        }

        StudentAttendancePeriod effectivePeriod = period == null ? StudentAttendancePeriod.ALL : period;
        LocalDate effectiveDate = referenceDate == null ? LocalDate.now(zoneId) : referenceDate;
        Page<Student> students = studentRepository.searchDepartmentStudents(
                teacher.getDepartment().getId(),
                studyYear,
                normalizeQuery(query),
                PageRequest.of(page, size, Sort.by("studentNumber").ascending())
        );
        Map<UUID, StudentAttendanceSummaryResponse> summaries = summaryService.calculateAll(
                students.getContent(),
                effectivePeriod,
                effectiveDate
        );
        List<TeacherStudentOverallAttendanceResponse> rows = students.getContent().stream()
                .map(student -> row(student, summaries.get(student.getId())))
                .toList();
        Page<TeacherStudentOverallAttendanceResponse> responsePage = new PageImpl<>(
                rows,
                students.getPageable(),
                students.getTotalElements()
        );

        return new TeacherCohortAttendanceResponse(
                teacher.getId(),
                teacher.getDepartment().getId(),
                teacher.getDepartment().getCode(),
                teacher.getDepartment().getName(),
                studyYear,
                effectivePeriod,
                effectiveDate,
                effectivePeriod.from(effectiveDate),
                effectivePeriod.to(effectiveDate),
                Instant.now(),
                StudentAttendanceSummaryService.CALCULATION_METHOD,
                PageResponse.from(responsePage)
        );
    }

    private TeacherStudentOverallAttendanceResponse row(
            Student student,
            StudentAttendanceSummaryResponse summary
    ) {
        return new TeacherStudentOverallAttendanceResponse(
                student.getId(),
                student.getUser().getId(),
                student.getStudentNumber(),
                student.getUser().getFirstName() + " " + student.getUser().getLastName(),
                student.getUser().getEmail(),
                student.getStudyYear(),
                summary.courseCount(),
                summary.averageAttendancePercentage(),
                summary.totalEligibleRollCalls(),
                summary.totalPresentRollCalls(),
                summary.totalAbsentRollCalls()
        );
    }

    private UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated subject is invalid");
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
