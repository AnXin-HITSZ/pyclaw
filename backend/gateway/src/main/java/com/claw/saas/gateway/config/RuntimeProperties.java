package com.claw.saas.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.runtime")
public record RuntimeProperties(
        String internalToken
) {
}
