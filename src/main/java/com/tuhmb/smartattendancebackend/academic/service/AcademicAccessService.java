package com.tuhmb.smartattendancebackend.academic.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AcademicAccessService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;

    public AcademicAccessService(TeacherRepository teacherRepository, StudentRepository studentRepository) {
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
    }

    public boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }

    public void requireCourseTeacherOrAdmin(Course course, Jwt jwt) {
        if (isAdmin(jwt)) {
            return;
        }
        Teacher teacher = teacherRepository.findByUserId(subject(jwt))
                .orElseThrow(() -> new AccessDeniedException("Teacher profile is required"));
        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Only the assigned course teacher may perform this action");
        }
    }

    public void requireStudentOwnerOrAdmin(UUID studentId, Jwt jwt) {
        if (isAdmin(jwt)) {
            return;
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found"));
        if (!student.getUser().getId().equals(subject(jwt))) {
            throw new AccessDeniedException("Students may only access their own enrollments");
        }
    }

    public UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated subject is invalid");
        }
    }
}
