package com.anxin.pyclaw.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pyclaw.runtime")
public record PyclawRuntimeProperties(
        String baseUrl,
        String apiToken,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
}
