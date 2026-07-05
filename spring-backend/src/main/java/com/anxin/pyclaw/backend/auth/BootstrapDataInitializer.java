package com.anxin.pyclaw.backend.auth;

import com.anxin.pyclaw.backend.config.PyclawSecurityProperties;
import com.anxin.pyclaw.backend.user.UserEntity;
import com.anxin.pyclaw.backend.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapDataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PyclawSecurityProperties properties;

    public BootstrapDataInitializer(UserRepository users, PasswordEncoder passwordEncoder, PyclawSecurityProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        users.findByUsername(properties.bootstrapAdminUsername()).ifPresentOrElse(user -> {
        }, () -> {
            OffsetDateTime now = OffsetDateTime.now();
            UserEntity admin = new UserEntity();
            admin.setId(UUID.randomUUID().toString());
            admin.setUsername(properties.bootstrapAdminUsername());
            admin.setDisplayName("Administrator");
            admin.setPasswordHash(passwordEncoder.encode(properties.bootstrapAdminPassword()));
            admin.setStatus("ACTIVE");
            admin.setAuthorities("user:manage,provider:manage,channel:manage,agent:run,audit:read,token:manage_self");
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            users.save(admin);
        });
    }
}
