package com.tuhmb.smartattendancebackend.auth.service;

import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.auth.api.RegisterUserRequest;
import com.tuhmb.smartattendancebackend.auth.api.RegisterUserResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public RegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public RegisterUserResponse register(RegisterUserRequest request) {
        RoleName roleName = requireSelfRegistrationRole(request.role());
        validateProfileFields(roleName, request.studentNumber(), request.studyYear(), request.employeeNumber());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already in use");
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role is missing: " + roleName));
        AppUser user = userRepository.save(new AppUser(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                Set.of(role)
        ));

        Student student = null;
        Teacher teacher = null;
        if (roleName == RoleName.STUDENT) {
            String studentNumber = request.studentNumber().trim();
            if (studentRepository.existsByStudentNumberIgnoreCase(studentNumber)) {
                throw new ConflictException("Student number is already in use");
            }
            student = new Student(user, studentNumber, request.studyYear());
            student.assignDepartment(department);
            studentRepository.save(student);
        } else {
            String employeeNumber = request.employeeNumber().trim();
            if (teacherRepository.existsByEmployeeNumberIgnoreCase(employeeNumber)) {
                throw new ConflictException("Employee number is already in use");
            }
            teacher = new Teacher(user, employeeNumber);
            teacher.assignDepartment(department);
            teacherRepository.save(teacher);
        }

        auditService.recordForUser(
                user.getId(),
                AuditAction.REGISTER,
                "AppUser",
                user.getId(),
                "Public " + roleName + " registration"
        );
        return new RegisterUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roleName,
                student == null ? null : student.getId(),
                student == null ? null : student.getStudentNumber(),
                student == null ? null : student.getStudyYear(),
                teacher == null ? null : teacher.getId(),
                teacher == null ? null : teacher.getEmployeeNumber(),
                department.getId(),
                department.getCode(),
                department.getName(),
                user.getCreatedAt()
        );
    }

    private RoleName requireSelfRegistrationRole(RoleName role) {
        if (role != RoleName.STUDENT && role != RoleName.TEACHER) {
            throw new IllegalArgumentException("role must be STUDENT or TEACHER");
        }
        return role;
    }

    private void validateProfileFields(
            RoleName role,
            String studentNumber,
            Integer studyYear,
            String employeeNumber
    ) {
        if (role == RoleName.STUDENT) {
            if (isBlank(studentNumber)) {
                throw new IllegalArgumentException("studentNumber is required for the STUDENT role");
            }
            if (studyYear == null) {
                throw new IllegalArgumentException("studyYear is required for the STUDENT role");
            }
            if (!isBlank(employeeNumber)) {
                throw new IllegalArgumentException("employeeNumber is not allowed for the STUDENT role");
            }
        } else {
            if (isBlank(employeeNumber)) {
                throw new IllegalArgumentException("employeeNumber is required for the TEACHER role");
            }
            if (!isBlank(studentNumber)) {
                throw new IllegalArgumentException("studentNumber is not allowed for the TEACHER role");
            }
            if (studyYear != null) {
                throw new IllegalArgumentException("studyYear is not allowed for the TEACHER role");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
