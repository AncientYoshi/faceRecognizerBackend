package com.tuhmb.smartattendancebackend.audit.service;

import com.tuhmb.smartattendancebackend.audit.api.AuditLogResponse;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.domain.AuditLog;
import com.tuhmb.smartattendancebackend.audit.repository.AuditLogRepository;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(
            AuditAction action,
            String entityType,
            Object entityId,
            String details
    ) {
        recordForUser(currentUserId(), action, entityType, entityId, details);
    }

    @Transactional
    public void recordForUser(
            UUID userId,
            AuditAction action,
            String entityType,
            Object entityId,
            String details
    ) {
        AppUser user = userId == null ? null : userRepository.findById(userId).orElse(null);
        auditLogRepository.save(new AuditLog(
                user,
                action,
                truncate(entityType, 80),
                entityId == null ? null : truncate(entityId.toString(), 100),
                truncate(details, 1000),
                currentIpAddress()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(
            AuditAction action,
            UUID userId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<AuditLog> specification = (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (userId != null) {
                predicates.add(builder.equal(root.get("user").get("id"), userId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<AuditLog> logs = auditLogRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.from(logs.map(AuditLogResponse::from));
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String currentIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return truncate(forwarded.split(",")[0].trim(), 64);
            }
            return truncate(request.getRemoteAddr(), 64);
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
