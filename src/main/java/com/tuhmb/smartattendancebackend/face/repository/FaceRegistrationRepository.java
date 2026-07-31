package com.tuhmb.smartattendancebackend.face.repository;

import com.tuhmb.smartattendancebackend.face.domain.FaceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FaceRegistrationRepository extends JpaRepository<FaceRegistration, UUID> {

    Optional<FaceRegistration> findByStudentId(UUID studentId);

    boolean existsByStudentId(UUID studentId);
}
