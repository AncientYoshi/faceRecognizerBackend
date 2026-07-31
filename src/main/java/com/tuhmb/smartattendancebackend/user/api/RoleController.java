package com.tuhmb.smartattendancebackend.user.api;

import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::from)
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .toList();
    }
}
