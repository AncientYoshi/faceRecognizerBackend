package com.tuhmb.smartattendancebackend.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedType;

    public TokenTypeValidator(String expectedType) {
        this.expectedType = expectedType;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (expectedType.equals(token.getClaimAsString("token_type"))) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "JWT is not a valid " + expectedType + " token",
                null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}
