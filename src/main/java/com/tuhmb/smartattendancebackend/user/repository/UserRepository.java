package com.tuhmb.smartattendancebackend.user.repository;

import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    @Override
    Optional<AppUser> findById(UUID id);

    boolean existsByEmailIgnoreCase(String email);
}
