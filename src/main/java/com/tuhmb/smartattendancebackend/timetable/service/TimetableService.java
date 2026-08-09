package com.tuhmb.smartattendancebackend.timetable.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.timetable.api.TimetableRequest;
import com.tuhmb.smartattendancebackend.timetable.api.TimetableResponse;
import com.tuhmb.smartattendancebackend.timetable.domain.TimetableEntry;
import com.tuhmb.smartattendancebackend.timetable.repository.TimetableRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Service
@Transactional
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final CourseRepository courseRepository;

    public TimetableService(
            TimetableRepository timetableRepository,
            CourseRepository courseRepository
    ) {
        this.timetableRepository = timetableRepository;
        this.courseRepository = courseRepository;
    }

    public TimetableResponse create(TimetableRequest request) {
        Course course = findCourse(request.courseId());

        boolean active = request.active() == null || request.active();

        validateRequest(request);
        validateConflicts(request, null, active);

        TimetableEntry timetable = new TimetableEntry(
                course,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                request.room(),
                request.effectiveFrom(),
                request.effectiveTo(),
                active
        );

        return toResponse(timetableRepository.save(timetable));
    }

    @Transactional(readOnly = true)
    public TimetableResponse getById(UUID id) {
        return toResponse(findTimetable(id));
    }

    @Transactional(readOnly = true)
    public Page<TimetableResponse> search(
            UUID courseId,
            DayOfWeek dayOfWeek,
            LocalDate onDate,
            Boolean active,
            Pageable pageable
    ) {
        return timetableRepository
                .search(
                        courseId,
                        dayOfWeek,
                        onDate,
                        active,
                        pageable
                )
                .map(this::toResponse);
    }

    public TimetableResponse update(
            UUID id,
            TimetableRequest request
    ) {
        TimetableEntry timetable = findTimetable(id);
        Course course = findCourse(request.courseId());

        boolean active = request.active() == null
                ? timetable.isActive()
                : request.active();

        validateRequest(request);
        validateConflicts(request, id, active);

        timetable.update(
                course,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                request.room(),
                request.effectiveFrom(),
                request.effectiveTo(),
                active
        );

        return toResponse(timetableRepository.save(timetable));
    }

    public void delete(UUID id) {
        TimetableEntry timetable = findTimetable(id);
        timetableRepository.delete(timetable);
    }

    private void validateRequest(TimetableRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException("startTime must be earlier than endTime");
        }

        LocalDate effectiveFrom = request.effectiveFrom();
        LocalDate effectiveTo = request.effectiveTo();

        if (effectiveFrom != null
                && effectiveTo != null
                && effectiveFrom.isAfter(effectiveTo)) {

            throw new IllegalArgumentException("effectiveFrom must not be later than effectiveTo");
        }
    }

    private void validateConflicts(
            TimetableRequest request,
            UUID excludedId,
            boolean resultingActive
    ) {
        if (!resultingActive) {
            return;
        }

        long courseConflicts;

        if (excludedId == null) {
            courseConflicts =
                    timetableRepository.countCourseConflicts(
                            request.courseId(),
                            request.dayOfWeek(),
                            request.startTime(),
                            request.endTime(),
                            request.effectiveFrom(),
                            request.effectiveTo()
                    );
        } else {
            courseConflicts =
                    timetableRepository.countCourseConflictsExcluding(
                            excludedId,
                            request.courseId(),
                            request.dayOfWeek(),
                            request.startTime(),
                            request.endTime(),
                            request.effectiveFrom(),
                            request.effectiveTo()
                    );
        }

        if (courseConflicts > 0) {
            throw new ConflictException("The course already has an overlapping timetable entry");
        }

        String room = normalizeRoom(request.room());

        if (room == null) {
            return;
        }

        long roomConflicts;

        if (excludedId == null) {
            roomConflicts =
                    timetableRepository.countRoomConflicts(
                            room,
                            request.dayOfWeek(),
                            request.startTime(),
                            request.endTime(),
                            request.effectiveFrom(),
                            request.effectiveTo()
                    );
        } else {
            roomConflicts =
                    timetableRepository.countRoomConflictsExcluding(
                            excludedId,
                            room,
                            request.dayOfWeek(),
                            request.startTime(),
                            request.endTime(),
                            request.effectiveFrom(),
                            request.effectiveTo()
                    );
        }

        if (roomConflicts > 0) {
            throw new ConflictException("The selected room is already used during this period");
        }
    }

    private Course findCourse(UUID courseId) {
        return courseRepository
                .findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course was not found"));
    }

    private TimetableEntry findTimetable(UUID id) {
        return timetableRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry was not found"));
    }

    private TimetableResponse toResponse(
            TimetableEntry timetable
    ) {
        Course course = timetable.getCourse();

        return new TimetableResponse(
                timetable.getId(),
                course.getId(),
                course.getCode(),
                course.getName(),
                timetable.getDayOfWeek(),
                timetable.getStartTime(),
                timetable.getEndTime(),
                timetable.getRoom(),
                timetable.getEffectiveFrom(),
                timetable.getEffectiveTo(),
                timetable.isActive(),
                timetable.getCreatedAt(),
                timetable.getUpdatedAt()
        );
    }

    private String normalizeRoom(String room) {
        if (room == null || room.isBlank()) {
            return null;
        }

        return room.trim();
    }
}
