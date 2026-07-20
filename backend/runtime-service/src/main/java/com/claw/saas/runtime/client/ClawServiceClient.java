package com.claw.saas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for claw-service calls (Claw CRUD, ClawAgent queries).
 * TODO: Implement HTTP client to claw-service REST API.
 */
@Component
public class ClawServiceClient {
    private static final Logger log = LoggerFactory.getLogger(ClawServiceClient.class);

    public boolean clawExists(String clawId) {
        log.warn("ClawServiceClient.clawExists is a stub — always returns true");
        return true;
    }

    public String getClawOwnerUserId(String clawId) {
        log.warn("ClawServiceClient.getClawOwnerUserId is a stub");
        return null;
    }

    public boolean isAgentEnabled(String clawId, String agentInstanceId) {
        log.warn("ClawServiceClient.isAgentEnabled is a stub — always returns true");
        return true;
    }
}
