package com.tuhmb.smartattendancebackend.auth.repository;

import com.tuhmb.smartattendancebackend.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            join fetch token.user user
            left join fetch user.roles
            where token.jti = :jti
            """)
    Optional<RefreshToken> findForUpdateByJti(@Param("jti") String jti);

    long deleteByExpiresAtBefore(Instant cutoff);
}
