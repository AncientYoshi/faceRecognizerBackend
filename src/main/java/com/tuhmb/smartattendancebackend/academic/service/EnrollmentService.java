package com.tuhmb.smartattendancebackend.academic.service;

import com.tuhmb.smartattendancebackend.academic.api.CourseResponse;
import com.tuhmb.smartattendancebackend.academic.api.EnrollmentResponse;
import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.domain.Enrollment;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseService courseService;
    private final AcademicAccessService accessService;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            StudentRepository studentRepository,
            CourseService courseService,
            AcademicAccessService accessService
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseService = courseService;
        this.accessService = accessService;
    }

    @Transactional
    public EnrollmentResponse enroll(UUID courseId, UUID studentId) {
        Course course = courseService.findCourse(courseId);
        Student student = findStudent(studentId);
        if (student.getDepartment() == null
                || !student.getDepartment().getId().equals(course.getDepartment().getId())) {
            throw new ConflictException("Student must belong to the course department");
        }
        if (student.getStudyYear() == null) {
            throw new ConflictException("Student study year must be assigned before enrollment");
        }
        if (course.getStudyYear() == null) {
            throw new ConflictException("Course study year must be assigned before enrollment");
        }
        if (!student.getStudyYear().equals(course.getStudyYear())) {
            throw new ConflictException("Student and course must have the same study year");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new ConflictException("Student is already enrolled in this course");
        }
        return EnrollmentResponse.from(enrollmentRepository.save(new Enrollment(student, course)));
    }

    @Transactional
    public void unenroll(UUID courseId, UUID studentId) {
        Enrollment enrollment = enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment was not found"));
        enrollmentRepository.delete(enrollment);
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> listCourse(
            UUID courseId,
            int page,
            int size,
            Jwt jwt
    ) {
        Course course = courseService.findCourse(courseId);
        accessService.requireCourseTeacherOrAdmin(course, jwt);
        return PageResponse.from(enrollmentRepository.findByCourseId(
                courseId,
                PageRequest.of(page, size, Sort.by("createdAt"))
        ).map(EnrollmentResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listStudentCourses(
            UUID studentId,
            int page,
            int size,
            Jwt jwt
    ) {
        accessService.requireStudentOwnerOrAdmin(studentId, jwt);
        findStudent(studentId);
        return PageResponse.from(enrollmentRepository.findByStudentId(
                studentId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(enrollment -> CourseResponse.from(
                enrollment.getCourse(),
                enrollmentRepository.countByCourseId(enrollment.getCourse().getId())
        )));
    }

    private Student findStudent(UUID id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found"));
    }
}
