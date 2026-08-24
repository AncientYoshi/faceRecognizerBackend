package com.tuhmb.smartattendancebackend.hardware.service;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.academic.repository.CourseRepository;
import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.common.api.PageResponse;
import com.tuhmb.smartattendancebackend.common.exception.ConflictException;
import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.hardware.api.HardwareDeviceCreateRequest;
import com.tuhmb.smartattendancebackend.hardware.api.HardwareDeviceResponse;
import com.tuhmb.smartattendancebackend.hardware.api.HardwareDeviceUpdateRequest;
import com.tuhmb.smartattendancebackend.hardware.domain.HardwareDevice;
import com.tuhmb.smartattendancebackend.hardware.repository.HardwareDeviceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HardwareDeviceService {

    private final HardwareDeviceRepository deviceRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public HardwareDeviceService(
            HardwareDeviceRepository deviceRepository,
            CourseRepository courseRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.deviceRepository = deviceRepository;
        this.courseRepository = courseRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<HardwareDeviceResponse> search(
            String query,
            Boolean enabled,
            int page,
            int size
    ) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Specification<HardwareDevice> specification = (root, ignored, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!normalized.isBlank()) {
                String pattern = "%" + normalized + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("deviceId")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("room")), pattern)
                ));
            }
            if (enabled != null) {
                predicates.add(builder.equal(root.get("enabled"), enabled));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<HardwareDevice> devices = deviceRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by("deviceId"))
        );
        return PageResponse.from(devices.map(HardwareDeviceResponse::from));
    }

    @Transactional(readOnly = true)
    public HardwareDeviceResponse get(UUID id) {
        return HardwareDeviceResponse.from(findDevice(id));
    }

    @Transactional
    public HardwareDeviceResponse create(HardwareDeviceCreateRequest request) {
        String deviceId = normalizeDeviceId(request.deviceId());
        ensureDeviceIdAvailable(deviceId);
        String room = normalizeRoom(request.room());
        Course course = findOptionalCourse(request.courseId());
        requireBinding(room, course);
        HardwareDevice device = deviceRepository.save(new HardwareDevice(
                deviceId,
                request.name().trim(),
                passwordEncoder.encode(request.deviceKey()),
                room,
                course,
                request.enabled() == null || request.enabled()
        ));
        auditService.record(AuditAction.REGISTER, "HardwareDevice", device.getId(), "Hardware device registered");
        return HardwareDeviceResponse.from(device);
    }

    @Transactional
    public HardwareDeviceResponse update(UUID id, HardwareDeviceUpdateRequest request) {
        HardwareDevice device = findDevice(id);
        String room = normalizeRoom(request.room());
        Course course = findOptionalCourse(request.courseId());
        requireBinding(room, course);
        device.update(request.name().trim(), room, course, request.enabled());
        if (request.newDeviceKey() != null) {
            device.rotateKey(passwordEncoder.encode(request.newDeviceKey()));
        }
        auditService.record(AuditAction.UPDATE, "HardwareDevice", id, "Hardware device updated");
        return HardwareDeviceResponse.from(device);
    }

    @Transactional
    public void delete(UUID id) {
        HardwareDevice device = findDevice(id);
        deviceRepository.delete(device);
        auditService.record(AuditAction.DELETE, "HardwareDevice", id, "Hardware device deleted");
    }

    private HardwareDevice findDevice(UUID id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hardware device was not found"));
    }

    private Course findOptionalCourse(UUID courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course was not found"));
    }

    private void requireBinding(String room, Course course) {
        if (room == null && course == null) {
            throw new ConflictException("Hardware device must be bound to a room, a course, or both");
        }
    }

    private void ensureDeviceIdAvailable(String deviceId) {
        if (deviceRepository.findByDeviceIdIgnoreCase(deviceId).isPresent()) {
            throw new ConflictException("Hardware device ID is already in use");
        }
    }

    private String normalizeDeviceId(String deviceId) {
        return deviceId.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRoom(String room) {
        return room == null || room.isBlank() ? null : room.trim();
    }
}
