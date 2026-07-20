package com.clawsaas.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "runtime.fastapi")
public record RuntimeProperties(
        String baseUrl,
        String apiToken,
        String internalToken,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public RuntimeProperties {
        if (internalToken == null || internalToken.isBlank()) {
            internalToken = apiToken;
        }
    }
}
