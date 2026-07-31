package com.tuhmb.smartattendancebackend.auth.service;

import com.tuhmb.smartattendancebackend.auth.exception.InvalidTokenException;
import com.tuhmb.smartattendancebackend.config.JwtProperties;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final JwtProperties properties;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            JwtProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.properties = properties;
    }

    public IssuedToken issueAccessToken(AppUser user) {
        return issue(user, "access", properties.accessTokenTtl());
    }

    public IssuedToken issueRefreshToken(AppUser user) {
        return issue(user, "refresh", properties.refreshTokenTtl());
    }

    public Jwt decodeRefreshToken(String token) {
        try {
            return refreshJwtDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("Refresh token is invalid or expired", exception);
        }
    }

    private IssuedToken issue(AppUser user, String tokenType, Duration lifetime) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(lifetime);
        String jti = UUID.randomUUID().toString();
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .id(jti)
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("token_type", tokenType)
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new IssuedToken(value, jti, expiresAt);
    }

    public record IssuedToken(String value, String jti, Instant expiresAt) {
    }
}
