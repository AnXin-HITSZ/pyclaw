package com.clawsaas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for session service calls (runtime session management).
 * TODO: Implement HTTP client to claw-service REST API.
 */
@Component
public class SessionServiceClient {
    private static final Logger log = LoggerFactory.getLogger(SessionServiceClient.class);

    public void requireOwned(String sessionId, String userId) {
        log.warn("SessionServiceClient.requireOwned is a stub — always passes");
    }
}
