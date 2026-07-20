package com.claw.saas.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "runtime.encryption")
public record RuntimeSecurityProperties(
        String secret
) {
}
