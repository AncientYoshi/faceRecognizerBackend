package com.tuhmb.smartattendancebackend.academic.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.academic.api.DepartmentRequest;
import com.tuhmb.smartattendancebackend.academic.api.DepartmentResponse;
import com.tuhmb.smartattendancebackend.academic.api.DepartmentStudentResponse;
import com.tuhmb.smartattendancebackend.academic.api.DepartmentTeacherResponse;
import com.tuhmb.smartattendancebackend.academic.api.ProfileAssignmentResponse;
import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.academic.repository.DepartmentRepository;
import com.tuhmb.smartattendancebackend.academic.repository.EnrollmentRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import com.tuhmb.smartattendancebackend.user.repository.StudentRepository;
import com.tuhmb.smartattendancebackend.user.repository.TeacherRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AuditService auditService;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            AuditService auditService
    ) {
        this.departmentRepository = departmentRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> search(String query, int page, int size) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Specification<Department> specification = (root, ignored, builder) -> {
            if (normalized.isBlank()) {
                return builder.conjunction();
            }
            String pattern = "%" + normalized + "%";
            Predicate code = builder.like(builder.lower(root.get("code")), pattern);
            Predicate name = builder.like(builder.lower(root.get("name")), pattern);
            return builder.or(code, name);
        };
        Page<Department> result = departmentRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by("code"))
        );
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(UUID id) {
        return toResponse(findDepartment(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentTeacherResponse> listTeachers(
            UUID departmentId,
            String query,
            int page,
            int size
    ) {
        findDepartment(departmentId);
        String pattern = searchPattern(query);
        Page<Teacher> result = teacherRepository.searchDepartmentTeachers(
                departmentId,
                pattern,
                PageRequest.of(page, size, Sort.by("employeeNumber"))
        );
        return PageResponse.from(result.map(DepartmentTeacherResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentStudentResponse> listStudents(
            UUID departmentId,
            String query,
            int page,
            int size
    ) {
        findDepartment(departmentId);
        String pattern = searchPattern(query);
        Page<Student> result = studentRepository.searchDepartmentStudents(
                departmentId,
                pattern,
                PageRequest.of(page, size, Sort.by("studentNumber"))
        );
        return PageResponse.from(result.map(DepartmentStudentResponse::from));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String code = normalizeCode(request.code());
        ensureUnique(code, request.name().trim(), null);
        return toResponse(departmentRepository.save(
                new Department(code, request.name().trim(), trimToNull(request.description()))
        ));
    }

    @Transactional
    public DepartmentResponse update(UUID id, DepartmentRequest request) {
        Department department = findDepartment(id);
        String code = normalizeCode(request.code());
        ensureUnique(code, request.name().trim(), id);
        department.update(code, request.name().trim(), trimToNull(request.description()));
        auditService.record(AuditAction.UPDATE, "Department", id, "Department updated");
        return toResponse(department);
    }

    @Transactional
    public void delete(UUID id) {
        Department department = findDepartment(id);
        if (courseRepository.countByDepartmentId(id) > 0) {
            throw new ConflictException("Department cannot be deleted while it has courses");
        }
        departmentRepository.delete(department);
        auditService.record(AuditAction.DELETE, "Department", id, "Department deleted");
    }

    @Transactional
    public ProfileAssignmentResponse assignStudent(UUID departmentId, UUID studentId) {
        Department department = findDepartment(departmentId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found"));
        if (student.getDepartment() != null
                && !student.getDepartment().getId().equals(departmentId)
                && enrollmentRepository.countByStudentId(studentId) > 0) {
            throw new ConflictException("Student cannot change departments while enrolled in courses");
        }
        student.assignDepartment(department);
        return studentAssignment(student);
    }

    @Transactional
    public ProfileAssignmentResponse unassignStudent(UUID departmentId, UUID studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student was not found"));
        requireAssignedDepartment(student.getDepartment(), departmentId);
        if (enrollmentRepository.countByStudentId(studentId) > 0) {
            throw new ConflictException("Student cannot be unassigned while enrolled in courses");
        }
        student.assignDepartment(null);
        return studentAssignment(student);
    }

    @Transactional
    public ProfileAssignmentResponse assignTeacher(UUID departmentId, UUID teacherId) {
        Department department = findDepartment(departmentId);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher was not found"));
        if (teacher.getDepartment() != null
                && !teacher.getDepartment().getId().equals(departmentId)
                && courseRepository.countByTeacherId(teacherId) > 0) {
            throw new ConflictException("Teacher cannot change departments while responsible for courses");
        }
        teacher.assignDepartment(department);
        return teacherAssignment(teacher);
    }

    @Transactional
    public ProfileAssignmentResponse unassignTeacher(UUID departmentId, UUID teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher was not found"));
        requireAssignedDepartment(teacher.getDepartment(), departmentId);
        if (courseRepository.countByTeacherId(teacherId) > 0) {
            throw new ConflictException("Teacher cannot be unassigned while responsible for courses");
        }
        teacher.assignDepartment(null);
        return teacherAssignment(teacher);
    }

    private Department findDepartment(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found"));
    }

    private DepartmentResponse toResponse(Department department) {
        UUID id = department.getId();
        return DepartmentResponse.from(
                department,
                studentRepository.countByDepartmentId(id),
                teacherRepository.countByDepartmentId(id),
                courseRepository.countByDepartmentId(id)
        );
    }

    private ProfileAssignmentResponse studentAssignment(Student student) {
        Department department = student.getDepartment();
        return new ProfileAssignmentResponse(
                student.getId(),
                student.getUser().getId(),
                fullName(student.getUser().getFirstName(), student.getUser().getLastName()),
                student.getStudentNumber(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName()
        );
    }

    private ProfileAssignmentResponse teacherAssignment(Teacher teacher) {
        Department department = teacher.getDepartment();
        return new ProfileAssignmentResponse(
                teacher.getId(),
                teacher.getUser().getId(),
                fullName(teacher.getUser().getFirstName(), teacher.getUser().getLastName()),
                teacher.getEmployeeNumber(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName()
        );
    }

    private void ensureUnique(String code, String name, UUID currentId) {
        departmentRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Department code is already in use");
                });
        departmentRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Department name is already in use");
                });
    }

    private void requireAssignedDepartment(Department department, UUID expectedId) {
        if (department == null || !department.getId().equals(expectedId)) {
            throw new ConflictException("Profile is not assigned to this department");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String searchPattern(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "" : "%" + normalized + "%";
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String fullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
