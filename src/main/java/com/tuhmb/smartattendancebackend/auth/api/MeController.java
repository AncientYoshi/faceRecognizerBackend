package com.tuhmb.smartattendancebackend.auth.api;

import com.tuhmb.smartattendancebackend.common.exception.ResourceNotFoundException;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Authenticated user was not found");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
        return MeResponse.from(user);
    }
}
