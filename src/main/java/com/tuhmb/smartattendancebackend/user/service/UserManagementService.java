package com.tuhmb.smartattendancebackend.user.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.api.CreateUserRequest;
import com.tuhmb.smartattendancebackend.user.api.UpdateUserRequest;
import com.tuhmb.smartattendancebackend.user.api.UserResponse;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Specification<AppUser> specification = (root, criteriaQuery, builder) -> {
            if (normalizedQuery.isBlank()) {
                return builder.conjunction();
            }
            String pattern = "%" + normalizedQuery + "%";
            Predicate email = builder.like(builder.lower(root.get("email")), pattern);
            Predicate firstName = builder.like(builder.lower(root.get("firstName")), pattern);
            Predicate lastName = builder.like(builder.lower(root.get("lastName")), pattern);
            return builder.or(email, firstName, lastName);
        };
        Page<AppUser> users = userRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.from(users.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return toResponse(findUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, null);
        Set<Role> roles = resolveRoles(request.roles());
        validateProfileFields(request.roles(), request.studentNumber(), request.studyYear(), request.employeeNumber());

        AppUser user = userRepository.save(new AppUser(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                roles
        ));
        syncProfiles(user, request.roles(), request.studentNumber(), request.studyYear(), request.employeeNumber());
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        AppUser user = findUser(id);
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, id);
        validateProfileFields(request.roles(), request.studentNumber(), request.studyYear(), request.employeeNumber());
        Set<Role> roles = resolveRoles(request.roles());

        user.updateProfile(
                email,
                request.firstName().trim(),
                request.lastName().trim(),
                request.enabled(),
                roles
        );
        if (request.password() != null && !request.password().isBlank()) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        syncProfiles(user, request.roles(), request.studentNumber(), request.studyYear(), request.employeeNumber());
        auditService.record(AuditAction.UPDATE, "AppUser", id, "User account updated");
        return toResponse(user);
    }

    @Transactional
    public void delete(UUID id, UUID currentUserId) {
        if (id.equals(currentUserId)) {
            throw new ConflictException("You cannot delete your own account");
        }
        AppUser user = findUser(id);
        userRepository.delete(user);
        auditService.record(AuditAction.DELETE, "AppUser", id, "User account deleted");
    }

    private void syncProfiles(
            AppUser user,
            Set<RoleName> roles,
            String studentNumber,
            Integer studyYear,
            String employeeNumber
    ) {
        if (roles.contains(RoleName.STUDENT)) {
            String normalized = studentNumber.trim();
            Student student = studentRepository.findByUserId(user.getId()).orElse(null);
            ensureStudentNumberAvailable(normalized, student);
            if (student == null) {
                studentRepository.save(new Student(user, normalized, studyYear));
            } else {
                student.updateStudentNumber(normalized);
                student.updateStudyYear(studyYear);
            }
        } else {
            studentRepository.deleteByUserId(user.getId());
        }

        if (roles.contains(RoleName.TEACHER)) {
            String normalized = employeeNumber.trim();
            Teacher teacher = teacherRepository.findByUserId(user.getId()).orElse(null);
            ensureEmployeeNumberAvailable(normalized, teacher);
            if (teacher == null) {
                teacherRepository.save(new Teacher(user, normalized));
            } else {
                teacher.updateEmployeeNumber(normalized);
            }
        } else {
            teacherRepository.deleteByUserId(user.getId());
        }
    }

    private UserResponse toResponse(AppUser user) {
        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        Teacher teacher = teacherRepository.findByUserId(user.getId()).orElse(null);
        return UserResponse.from(
                user,
                student == null ? null : student.getId(),
                student == null ? null : student.getStudentNumber(),
                student == null ? null : student.getStudyYear(),
                teacher == null ? null : teacher.getId(),
                teacher == null ? null : teacher.getEmployeeNumber()
        );
    }

    private AppUser findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
    }

    private Set<Role> resolveRoles(Set<RoleName> names) {
        Set<Role> roles = new HashSet<>();
        for (RoleName name : names) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new IllegalStateException("Role is missing: " + name)));
        }
        return roles;
    }

    private void validateProfileFields(
            Set<RoleName> roles,
            String studentNumber,
            Integer studyYear,
            String employeeNumber
    ) {
        if (roles.contains(RoleName.STUDENT) && roles.contains(RoleName.TEACHER)) {
            throw new ConflictException("A user cannot have both STUDENT and TEACHER roles");
        }
        if (roles.contains(RoleName.STUDENT) && isBlank(studentNumber)) {
            throw new ConflictException("studentNumber is required for the STUDENT role");
        }
        if (roles.contains(RoleName.STUDENT) && studyYear == null) {
            throw new ConflictException("studyYear is required for the STUDENT role");
        }
        if (roles.contains(RoleName.TEACHER) && isBlank(employeeNumber)) {
            throw new ConflictException("employeeNumber is required for the TEACHER role");
        }
        if (!roles.contains(RoleName.STUDENT) && studyYear != null) {
            throw new ConflictException("studyYear is only allowed for the STUDENT role");
        }
    }

    private void ensureEmailAvailable(String email, UUID currentUserId) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ConflictException("Email is already in use");
                });
    }

    private void ensureStudentNumberAvailable(String number, Student current) {
        studentRepository.findByStudentNumberIgnoreCase(number).stream()
                .filter(student -> current == null || !student.getId().equals(current.getId()))
                .findAny()
                .ifPresent(student -> {
                    throw new ConflictException("Student number is already in use");
                });
    }

    private void ensureEmployeeNumberAvailable(String number, Teacher current) {
        teacherRepository.findByEmployeeNumberIgnoreCase(number).stream()
                .filter(teacher -> current == null || !teacher.getId().equals(current.getId()))
                .findAny()
                .ifPresent(teacher -> {
                    throw new ConflictException("Employee number is already in use");
                });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
