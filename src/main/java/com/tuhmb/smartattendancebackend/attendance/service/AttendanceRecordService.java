package com.tuhmb.smartattendancebackend.attendance.service;

import com.tuhmb.smartattendancebackend.academic.service.AcademicAccessService;
import com.tuhmb.smartattendancebackend.attendance.api.AttendanceResponse;
import com.tuhmb.smartattendancebackend.attendance.domain.Attendance;
import com.tuhmb.smartattendancebackend.attendance.repository.AttendanceRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceRecordService {

    private final AttendanceRepository attendanceRepository;
    private final AcademicAccessService accessService;

    public AttendanceRecordService(
            AttendanceRepository attendanceRepository,
            AcademicAccessService accessService
    ) {
        this.attendanceRepository = attendanceRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> search(
            UUID sessionId,
            UUID courseId,
            UUID studentId,
            int page,
            int size,
            Jwt jwt
    ) {
        UUID userId = accessService.subject(jwt);
        List<String> roles = jwt.getClaimAsStringList("roles");
        Specification<Attendance> specification = (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null) {
                predicates.add(builder.equal(root.get("session").get("id"), sessionId));
            }
            if (courseId != null) {
                predicates.add(builder.equal(root.get("course").get("id"), courseId));
            }
            if (studentId != null) {
                predicates.add(builder.equal(root.get("student").get("id"), studentId));
            }
            if (roles != null && roles.contains("ADMIN")) {
                return builder.and(predicates.toArray(Predicate[]::new));
            }
            if (roles != null && roles.contains("TEACHER")) {
                predicates.add(builder.equal(
                        root.get("course").get("teacher").get("user").get("id"),
                        userId
                ));
            } else if (roles != null && roles.contains("STUDENT")) {
                predicates.add(builder.equal(root.get("student").get("user").get("id"), userId));
            } else {
                throw new AccessDeniedException("Attendance records are not available for this role");
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Attendance> records = attendanceRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "verifiedAt"))
        );
        return PageResponse.from(records.map(AttendanceResponse::from));
    }

    @Transactional(readOnly = true)
    public AttendanceResponse get(UUID id, Jwt jwt) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record was not found"));
        authorize(attendance, jwt);
        return AttendanceResponse.from(attendance);
    }

    private void authorize(Attendance attendance, Jwt jwt) {
        if (accessService.isAdmin(jwt)) {
            return;
        }
        UUID userId = accessService.subject(jwt);
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null
                && roles.contains("STUDENT")
                && attendance.getStudent().getUser().getId().equals(userId)) {
            return;
        }
        if (roles != null
                && roles.contains("TEACHER")
                && attendance.getCourse().getTeacher().getUser().getId().equals(userId)) {
            return;
        }
        throw new AccessDeniedException("You cannot access this attendance record");
    }
}
