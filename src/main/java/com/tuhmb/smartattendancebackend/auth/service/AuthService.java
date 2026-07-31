package com.tuhmb.smartattendancebackend.auth.service;

import com.tuhmb.smartattendancebackend.audit.domain.AuditAction;
import com.tuhmb.smartattendancebackend.audit.service.AuditService;
import com.tuhmb.smartattendancebackend.auth.api.AuthResponse;
import com.tuhmb.smartattendancebackend.auth.api.LoginRequest;
import com.tuhmb.smartattendancebackend.auth.domain.RefreshToken;
import com.tuhmb.smartattendancebackend.auth.exception.InvalidTokenException;
import com.tuhmb.smartattendancebackend.auth.repository.RefreshTokenRepository;
import com.tuhmb.smartattendancebackend.config.JwtProperties;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password())
            );
        } catch (AuthenticationException exception) {
            throw new BadCredentialsException("Invalid email or password", exception);
        }

        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        AuthResponse response = issueTokenPair(user);
        auditService.recordForUser(user.getId(), AuditAction.LOGIN, "AppUser", user.getId(), "Successful login");
        return response;
    }

    @Transactional
    public AuthResponse refresh(String encodedRefreshToken) {
        Jwt jwt = jwtService.decodeRefreshToken(encodedRefreshToken);
        String jti = requireClaim(jwt.getId(), "Refresh token has no identifier");
        UUID subject = parseSubject(jwt.getSubject());
        Instant now = Instant.now();

        RefreshToken storedToken = refreshTokenRepository.findForUpdateByJti(jti)
                .orElseThrow(() -> new InvalidTokenException("Refresh token was revoked or is unknown"));
        if (!storedToken.isUsableAt(now)) {
            throw new InvalidTokenException("Refresh token was revoked or expired");
        }
        if (!storedToken.getUser().getId().equals(subject)) {
            throw new InvalidTokenException("Refresh token subject does not match its owner");
        }
        if (!storedToken.getUser().isEnabled()) {
            throw new InvalidTokenException("User account is disabled");
        }

        storedToken.revoke(now);
        return issueTokenPair(storedToken.getUser());
    }

    @Transactional
    public void logout(String encodedRefreshToken) {
        Jwt jwt = jwtService.decodeRefreshToken(encodedRefreshToken);
        String jti = requireClaim(jwt.getId(), "Refresh token has no identifier");
        refreshTokenRepository.findForUpdateByJti(jti)
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private AuthResponse issueTokenPair(AppUser user) {
        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(user);
        JwtService.IssuedToken refreshToken = jwtService.issueRefreshToken(user);
        refreshTokenRepository.save(
                new RefreshToken(refreshToken.jti(), user, refreshToken.expiresAt())
        );
        return new AuthResponse(
                accessToken.value(),
                refreshToken.value(),
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UUID parseSubject(String subject) {
        try {
            return UUID.fromString(requireClaim(subject, "Refresh token has no subject"));
        } catch (IllegalArgumentException exception) {
            throw new InvalidTokenException("Refresh token subject is invalid", exception);
        }
    }

    private String requireClaim(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidTokenException(message);
        }
        return value;
    }
}
