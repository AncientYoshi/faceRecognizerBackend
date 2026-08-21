package com.tuhmb.smartattendancebackend.academic.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.academic.api.CourseRequest;
import com.tuhmb.smartattendancebackend.academic.api.CourseResponse;
import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceSessionRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AuditService auditService;

    public CourseService(
            CourseRepository courseRepository,
            DepartmentRepository departmentRepository,
            TeacherRepository teacherRepository,
            EnrollmentRepository enrollmentRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            AuditService auditService
    ) {
        this.courseRepository = courseRepository;
        this.departmentRepository = departmentRepository;
        this.teacherRepository = teacherRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> search(
            String query,
            UUID departmentId,
            UUID teacherId,
            String semester,
            String academicYear,
            Integer studyYear,
            int page,
            int size
    ) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Specification<Course> specification = (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!normalized.isBlank()) {
                String pattern = "%" + normalized + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)
                ));
            }
            if (departmentId != null) {
                predicates.add(builder.equal(root.get("department").get("id"), departmentId));
            }
            if (teacherId != null) {
                predicates.add(builder.equal(root.get("teacher").get("id"), teacherId));
            }
            if (semester != null && !semester.isBlank()) {
                predicates.add(builder.equal(builder.lower(root.get("semester")), semester.trim().toLowerCase()));
            }
            if (academicYear != null && !academicYear.isBlank()) {
                predicates.add(builder.equal(root.get("academicYear"), academicYear.trim()));
            }
            if (studyYear != null) {
                predicates.add(builder.equal(root.get("studyYear"), studyYear));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Course> courses = courseRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by("code"))
        );
        return PageResponse.from(courses.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID id) {
        return toResponse(findCourse(id));
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        String code = normalizeCode(request.code());
        ensureCodeAvailable(code, null);
        Department department = findDepartment(request.departmentId());
        Teacher teacher = findTeacher(request.teacherId());
        requireTeacherDepartment(teacher, department);
        Course course = courseRepository.save(new Course(
                code,
                request.name().trim(),
                request.semester().trim(),
                request.academicYear().trim(),
                request.studyYear(),
                department,
                teacher
        ));
        return toResponse(course);
    }

    @Transactional
    public CourseResponse update(UUID id, CourseRequest request) {
        Course course = findCourse(id);
        String code = normalizeCode(request.code());
        ensureCodeAvailable(code, id);
        Department department = findDepartment(request.departmentId());
        Teacher teacher = findTeacher(request.teacherId());
        requireTeacherDepartment(teacher, department);
        if (enrollmentRepository.countStudentsOutsideStudyYear(id, request.studyYear()) > 0) {
            throw new ConflictException("Course study year must match every enrolled student");
        }
        course.update(
                code,
                request.name().trim(),
                request.semester().trim(),
                request.academicYear().trim(),
                request.studyYear(),
                department,
                teacher
        );
        auditService.record(AuditAction.UPDATE, "Course", id, "Course updated");
        return toResponse(course);
    }

    @Transactional
    public void delete(UUID id) {
        Course course = findCourse(id);
        if (attendanceSessionRepository.countByCourseId(id) > 0) {
            throw new ConflictException("Course cannot be deleted while it has attendance sessions");
        }
        courseRepository.delete(course);
        auditService.record(AuditAction.DELETE, "Course", id, "Course deleted");
    }

    public Course findCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course was not found"));
    }

    private CourseResponse toResponse(Course course) {
        return CourseResponse.from(course, enrollmentRepository.countByCourseId(course.getId()));
    }

    private Department findDepartment(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found"));
    }

    private Teacher findTeacher(UUID id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher was not found"));
    }

    private void requireTeacherDepartment(Teacher teacher, Department department) {
        if (teacher.getDepartment() == null || !teacher.getDepartment().getId().equals(department.getId())) {
            throw new ConflictException("Course teacher must be assigned to the selected department");
        }
    }

    private void ensureCodeAvailable(String code, UUID currentId) {
        courseRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Course code is already in use");
                });
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
