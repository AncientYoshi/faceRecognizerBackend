package com.tuhmb.smartattendancebackend.timetable.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.timetable.api.StudentTimetableEntryResponse;
import com.tuhmb.smartattendancebackend.timetable.api.StudentTimetableResponse;
import com.tuhmb.smartattendancebackend.timetable.domain.TimetableEntry;
import com.tuhmb.smartattendancebackend.timetable.repository.TimetableRepository;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class StudentTimetableService {

    private final TimetableRepository timetableRepository;
    private final StudentRepository studentRepository;
    private final ZoneId zoneId;

    public StudentTimetableService(
            TimetableRepository timetableRepository,
            StudentRepository studentRepository,
            @Value("${app.time-zone:Asia/Yangon}") String timeZone
    ) {
        this.timetableRepository = timetableRepository;
        this.studentRepository = studentRepository;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public StudentTimetableResponse getWeek(Jwt jwt, LocalDate requestedWeekStart) {
        Student student = studentRepository.findByUserId(subject(jwt))
                .orElseThrow(() -> new ResourceNotFoundException("Student profile was not found"));
        LocalDate today = LocalDate.now(zoneId);
        LocalDate referenceDate = requestedWeekStart == null ? today : requestedWeekStart;
        LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<StudentTimetableEntryResponse> entries = timetableRepository
                .findStudentEntriesForWeek(student.getId(), weekStart, weekEnd)
                .stream()
                .filter(entry -> isEffectiveOn(entry, occurrenceDate(entry, weekStart)))
                .map(entry -> toStudentResponse(entry, occurrenceDate(entry, weekStart), today))
                .toList();

        return new StudentTimetableResponse(
                student.getId(),
                today,
                weekStart,
                weekEnd,
                zoneId.getId(),
                entries
        );
    }

    private StudentTimetableEntryResponse toStudentResponse(
            TimetableEntry timetable,
            LocalDate date,
            LocalDate today
    ) {
        Course course = timetable.getCourse();
        String teacherName = course.getTeacher().getUser().getFirstName()
                + " "
                + course.getTeacher().getUser().getLastName();
        return new StudentTimetableEntryResponse(
                timetable.getId(),
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getSemester(),
                course.getAcademicYear(),
                course.getTeacher().getId(),
                teacherName,
                timetable.getDayOfWeek(),
                date,
                timetable.getStartTime(),
                timetable.getEndTime(),
                timetable.getRoom(),
                date.equals(today)
        );
    }

    private LocalDate occurrenceDate(TimetableEntry timetable, LocalDate weekStart) {
        return weekStart.plusDays(timetable.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private boolean isEffectiveOn(TimetableEntry timetable, LocalDate date) {
        return (timetable.getEffectiveFrom() == null || !timetable.getEffectiveFrom().isAfter(date))
                && (timetable.getEffectiveTo() == null || !timetable.getEffectiveTo().isBefore(date));
    }

    private UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Authenticated student was not found");
        }
    }
}
