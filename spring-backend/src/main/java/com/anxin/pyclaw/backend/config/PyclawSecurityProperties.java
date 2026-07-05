package com.anxin.pyclaw.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pyclaw.security")
public record PyclawSecurityProperties(
        String jwtSecret,
        long jwtTtlSeconds,
        String bootstrapAdminUsername,
        String bootstrapAdminPassword
) {
}
