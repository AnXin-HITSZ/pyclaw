package com.anxin.pyclaw.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pyclaw.runtime")
public record PyclawRuntimeProperties(
        String baseUrl,
        String apiToken,
        String internalToken,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public PyclawRuntimeProperties {
        if (internalToken == null || internalToken.isBlank()) {
            internalToken = apiToken;
        }
    }
}
