package com.tuhmb.smartattendancebackend.config;

import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import com.tuhmb.smartattendancebackend.user.domain.Role;
import com.tuhmb.smartattendancebackend.user.domain.RoleName;
import com.tuhmb.smartattendancebackend.user.repository.RoleRepository;
import com.tuhmb.smartattendancebackend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.password() == null || properties.password().length() < 8) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must contain at least 8 characters when admin bootstrap is enabled"
            );
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Bootstrap administrator already exists: {}", email);
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing from the database"));
        AppUser admin = new AppUser(
                email,
                passwordEncoder.encode(properties.password()),
                properties.firstName().trim(),
                properties.lastName().trim(),
                Set.of(adminRole)
        );
        userRepository.save(admin);
        log.info("Created bootstrap administrator: {}", email);
    }
}
